package org.wanshu.reader.core.queue;

public class FakeSchedulerClock implements SchedulerClock {

    private long currentTimeMs = 1000000L;
    private Runnable scheduledRunnable = null;
    private long scheduledTriggerTimeMs = -1L;

    @Override
    public synchronized long nowMs() {
        return currentTimeMs;
    }

    public synchronized void setTime(long timeMs) {
        this.currentTimeMs = timeMs;
    }

    public synchronized void advanceTime(long ms) {
        this.currentTimeMs += ms;
        triggerIfDue();
    }

    @Override
    public synchronized void scheduleAfter(long delayMs, Runnable runnable) {
        this.scheduledRunnable = runnable;
        this.scheduledTriggerTimeMs = this.currentTimeMs + delayMs;
    }

    @Override
    public synchronized void cancelScheduled() {
        this.scheduledRunnable = null;
        this.scheduledTriggerTimeMs = -1L;
    }

    public synchronized boolean hasScheduledTimer() {
        return scheduledRunnable != null;
    }

    public synchronized long getScheduledTriggerTimeMs() {
        return scheduledTriggerTimeMs;
    }

    public synchronized void triggerIfDue() {
        if (scheduledRunnable != null && currentTimeMs >= scheduledTriggerTimeMs) {
            Runnable r = scheduledRunnable;
            scheduledRunnable = null;
            scheduledTriggerTimeMs = -1L;
            r.run();
        }
    }
}
