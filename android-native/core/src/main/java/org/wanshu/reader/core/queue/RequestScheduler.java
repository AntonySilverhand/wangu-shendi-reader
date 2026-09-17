package org.wanshu.reader.core.queue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.wanshu.reader.core.net.CancellationToken;

public class RequestScheduler {

    public static final int MAX_CONCURRENCY = 2;
    public static final int MAX_NON_HIGH_CONCURRENCY = 1;
    public static final long MIN_GLOBAL_GAP_MS = 160L;
    public static final long AUTO_INITIAL_GAP_MS = 250L;
    public static final int CONSECUTIVE_SUCCESS_THRESHOLD = 10;

    private final SchedulerClock clock;
    private final ExecutorService executor;
    private final boolean ownsExecutor;
    private final SchedulerPumpRunnable pumpRunnable;
    private final TaskPriorityComparator priorityComparator = new TaskPriorityComparator();

    private final List<ScheduledTask> pendingTasks = new ArrayList<ScheduledTask>();
    private final Map<RequestKey, InFlightTask> inFlight = new HashMap<RequestKey, InFlightTask>();

    private long lastRequestStartTimeMs = 0L;
    private long cooldownUntilMs = 0L;
    private int consecutiveSuccessCount = 0;
    private boolean paused = false;
    private long sequenceCounter = 0L;

    public RequestScheduler(SchedulerClock clock, ExecutorService executor) {
        this.clock = clock != null ? clock : new SystemSchedulerClock();
        if (executor != null) {
            this.executor = executor;
            this.ownsExecutor = false;
        } else {
            this.executor = Executors.newFixedThreadPool(MAX_CONCURRENCY);
            this.ownsExecutor = true;
        }
        this.pumpRunnable = new SchedulerPumpRunnable(this);
    }

    public RequestScheduler(SchedulerClock clock) {
        this(clock, null);
    }

    public RequestScheduler() {
        this(new SystemSchedulerClock(), null);
    }

    public synchronized <T> void enqueue(
            RequestKey key,
            RequestPriority priority,
            Object subscriberId,
            SchedulerAction<T> action,
            SchedulerCallback<T> callback
    ) {
        if (key == null) return;
        if (priority == null) priority = RequestPriority.LOW;

        // 1. Check in-flight
        InFlightTask existingInFlight = inFlight.get(key);
        if (existingInFlight != null) {
            existingInFlight.boostPriority(priority);
            existingInFlight.addSubscription(subscriberId, callback);
            return;
        }

        // 2. Check pending
        for (int i = 0; i < pendingTasks.size(); i++) {
            ScheduledTask pending = pendingTasks.get(i);
            if (pending.getKey().equals(key)) {
                pending.boostPriority(priority);
                pending.addSubscription(subscriberId, callback);
                Collections.sort(pendingTasks, priorityComparator);
                pump();
                return;
            }
        }

        // 3. New pending task
        ScheduledTask newTask = new ScheduledTask(
                key,
                priority,
                sequenceCounter++,
                action,
                subscriberId,
                callback
        );
        pendingTasks.add(newTask);
        Collections.sort(pendingTasks, priorityComparator);

        pump();
    }

    public synchronized void cancelSubscriber(Object subscriberId, RequestKey key) {
        if (subscriberId == null) return;

        // Check pending
        for (int i = pendingTasks.size() - 1; i >= 0; i--) {
            ScheduledTask t = pendingTasks.get(i);
            if (key == null || t.getKey().equals(key)) {
                boolean empty = t.removeSubscriber(subscriberId);
                if (empty) {
                    pendingTasks.remove(i);
                }
            }
        }

        // Check in-flight
        for (InFlightTask t : inFlight.values()) {
            if (key == null || t.getKey().equals(key)) {
                boolean empty = t.removeSubscriber(subscriberId);
                if (empty) {
                    t.getCancellationToken().cancel();
                }
            }
        }

        pump();
    }

    public synchronized void cancelAllForSubscriber(Object subscriberId) {
        cancelSubscriber(subscriberId, null);
    }

    public synchronized void pause() {
        paused = true;
        clock.cancelScheduled();
    }

    public synchronized void resume() {
        if (paused) {
            paused = false;
            pump();
        }
    }

    public synchronized boolean isPaused() {
        return paused;
    }

    public synchronized void setCooldownSeconds(long seconds) {
        if (seconds > 0) {
            long until = clock.nowMs() + (seconds * 1000L);
            if (until > this.cooldownUntilMs) {
                this.cooldownUntilMs = until;
            }
            consecutiveSuccessCount = 0;
            pump();
        }
    }

    public synchronized long getCooldownUntilMs() {
        return cooldownUntilMs;
    }

    public synchronized int getPendingCount() {
        return pendingTasks.size();
    }

    public synchronized int getInFlightCount() {
        return inFlight.size();
    }

    public synchronized void pump() {
        if (paused) {
            return;
        }

        if (pendingTasks.isEmpty()) {
            clock.cancelScheduled();
            return;
        }

        if (inFlight.size() >= MAX_CONCURRENCY) {
            return;
        }

        long now = clock.nowMs();

        // 1. Check cooldown
        if (now < cooldownUntilMs) {
            long delay = cooldownUntilMs - now;
            clock.scheduleAfter(delay, pumpRunnable);
            return;
        }

        // 2. Count non-high priority tasks currently in-flight
        int nonHighInFlight = 0;
        for (InFlightTask task : inFlight.values()) {
            if (!task.getPriority().isHighPriority()) {
                nonHighInFlight++;
            }
        }

        // 3. Find candidate task
        ScheduledTask candidate = null;
        int candidateIndex = -1;

        for (int i = 0; i < pendingTasks.size(); i++) {
            ScheduledTask task = pendingTasks.get(i);
            if (task.getPriority().isHighPriority()) {
                // High priority can always use any free slot
                candidate = task;
                candidateIndex = i;
                break;
            } else {
                // Non-high priority can only use slot if nonHighInFlight < 1
                if (nonHighInFlight < MAX_NON_HIGH_CONCURRENCY) {
                    candidate = task;
                    candidateIndex = i;
                    break;
                }
            }
        }

        if (candidate == null) {
            // Slots full for candidate priorities
            return;
        }

        // 4. Check time gap
        long requiredGap = MIN_GLOBAL_GAP_MS;
        if (!candidate.getPriority().isHighPriority() && consecutiveSuccessCount < CONSECUTIVE_SUCCESS_THRESHOLD) {
            requiredGap = AUTO_INITIAL_GAP_MS;
        }

        long timeSinceLastStart = now - lastRequestStartTimeMs;
        if (timeSinceLastStart < requiredGap && lastRequestStartTimeMs > 0L) {
            long delay = requiredGap - timeSinceLastStart;
            clock.scheduleAfter(delay, pumpRunnable);
            return;
        }

        // 5. Dispatch candidate
        pendingTasks.remove(candidateIndex);

        CancellationToken token = new CancellationToken();
        InFlightTask inFlightTask = new InFlightTask(
                candidate.getKey(),
                candidate.getPriority(),
                token,
                now,
                candidate.getSubscriptions()
        );
        inFlight.put(candidate.getKey(), inFlightTask);
        lastRequestStartTimeMs = now;

        executor.execute(new SchedulerExecutionRunnable(this, inFlightTask, candidate.getAction()));

        // If slots are still available, attempt another pump (respecting interval)
        if (inFlight.size() < MAX_CONCURRENCY && !pendingTasks.isEmpty()) {
            clock.scheduleAfter(MIN_GLOBAL_GAP_MS, pumpRunnable);
        }
    }

    @SuppressWarnings("unchecked")
    synchronized void onTaskSucceeded(InFlightTask task, Object result) {
        inFlight.remove(task.getKey());
        consecutiveSuccessCount++;

        List<SubscriberSubscription> subs = task.getSubscriptions();
        for (int i = 0; i < subs.size(); i++) {
            SubscriberSubscription sub = subs.get(i);
            if (sub.getCallback() != null) {
                try {
                    ((SchedulerCallback<Object>) sub.getCallback()).onSuccess(result);
                } catch (Exception ignored) {}
            }
        }

        pump();
    }

    synchronized void onTaskFailed(InFlightTask task, Throwable error) {
        inFlight.remove(task.getKey());
        consecutiveSuccessCount = 0;

        List<SubscriberSubscription> subs = task.getSubscriptions();
        for (int i = 0; i < subs.size(); i++) {
            SubscriberSubscription sub = subs.get(i);
            if (sub.getCallback() != null) {
                try {
                    sub.getCallback().onError(error);
                } catch (Exception ignored) {}
            }
        }

        pump();
    }

    synchronized void onTaskCancelled(InFlightTask task) {
        inFlight.remove(task.getKey());

        List<SubscriberSubscription> subs = task.getSubscriptions();
        for (int i = 0; i < subs.size(); i++) {
            SubscriberSubscription sub = subs.get(i);
            if (sub.getCallback() != null) {
                try {
                    sub.getCallback().onCancelled();
                } catch (Exception ignored) {}
            }
        }

        pump();
    }

    public void shutdown() {
        pause();
        if (ownsExecutor) {
            executor.shutdownNow();
        }
    }
}
