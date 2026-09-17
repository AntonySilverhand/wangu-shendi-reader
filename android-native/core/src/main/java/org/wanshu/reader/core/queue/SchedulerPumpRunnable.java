package org.wanshu.reader.core.queue;

public class SchedulerPumpRunnable implements Runnable {
    private final RequestScheduler scheduler;

    public SchedulerPumpRunnable(RequestScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public void run() {
        if (scheduler != null) {
            scheduler.pump();
        }
    }
}
