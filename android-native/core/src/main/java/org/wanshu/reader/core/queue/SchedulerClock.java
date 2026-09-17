package org.wanshu.reader.core.queue;

public interface SchedulerClock {
    long nowMs();
    void scheduleAfter(long delayMs, Runnable runnable);
    void cancelScheduled();
}
