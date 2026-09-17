package org.wanshu.reader.core.queue;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class SystemSchedulerClock implements SchedulerClock {

    private final ScheduledExecutorService executor;
    private ScheduledFuture<?> pendingFuture;

    public SystemSchedulerClock() {
        this.executor = Executors.newSingleThreadScheduledExecutor();
    }

    @Override
    public long nowMs() {
        return System.currentTimeMillis();
    }

    @Override
    public synchronized void scheduleAfter(long delayMs, Runnable runnable) {
        cancelScheduled();
        if (runnable != null && !executor.isShutdown()) {
            pendingFuture = executor.schedule(runnable, Math.max(0L, delayMs), TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public synchronized void cancelScheduled() {
        if (pendingFuture != null && !pendingFuture.isDone()) {
            pendingFuture.cancel(false);
            pendingFuture = null;
        }
    }

    public void shutdown() {
        cancelScheduled();
        executor.shutdownNow();
    }
}
