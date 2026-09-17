package org.wanshu.reader.core.queue;

import java.util.ArrayList;
import java.util.List;
import org.wanshu.reader.core.net.CancellationToken;

public class InFlightTask {
    private final RequestKey key;
    private RequestPriority priority;
    private final CancellationToken cancellationToken;
    private final long startedAtMs;
    private final List<SubscriberSubscription> subscriptions = new ArrayList<SubscriberSubscription>();

    public InFlightTask(
            RequestKey key,
            RequestPriority priority,
            CancellationToken cancellationToken,
            long startedAtMs,
            List<SubscriberSubscription> initialSubscriptions
    ) {
        this.key = key;
        this.priority = priority;
        this.cancellationToken = cancellationToken;
        this.startedAtMs = startedAtMs;
        if (initialSubscriptions != null) {
            this.subscriptions.addAll(initialSubscriptions);
        }
    }

    public RequestKey getKey() {
        return key;
    }

    public RequestPriority getPriority() {
        return priority;
    }

    public void boostPriority(RequestPriority newPriority) {
        if (newPriority != null && newPriority.getLevel() > this.priority.getLevel()) {
            this.priority = newPriority;
        }
    }

    public CancellationToken getCancellationToken() {
        return cancellationToken;
    }

    public long getStartedAtMs() {
        return startedAtMs;
    }

    public List<SubscriberSubscription> getSubscriptions() {
        return subscriptions;
    }

    public void addSubscription(Object subscriberId, SchedulerCallback<?> callback) {
        if (subscriberId == null) return;
        for (int i = 0; i < subscriptions.size(); i++) {
            if (subscriptions.get(i).getSubscriberId().equals(subscriberId)) {
                return;
            }
        }
        subscriptions.add(new SubscriberSubscription(subscriberId, callback));
    }

    public boolean removeSubscriber(Object subscriberId) {
        if (subscriberId == null) return subscriptions.isEmpty();
        for (int i = 0; i < subscriptions.size(); i++) {
            if (subscriptions.get(i).getSubscriberId().equals(subscriberId)) {
                SubscriberSubscription sub = subscriptions.remove(i);
                if (sub.getCallback() != null) {
                    try {
                        sub.getCallback().onCancelled();
                    } catch (Exception ignored) {}
                }
                break;
            }
        }
        return subscriptions.isEmpty();
    }
}
