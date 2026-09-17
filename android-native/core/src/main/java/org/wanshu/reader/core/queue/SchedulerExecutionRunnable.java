package org.wanshu.reader.core.queue;

import org.wanshu.reader.core.net.CancellationToken;

public class SchedulerExecutionRunnable implements Runnable {

    private final RequestScheduler scheduler;
    private final InFlightTask inFlightTask;
    private final SchedulerAction<?> action;

    public SchedulerExecutionRunnable(
            RequestScheduler scheduler,
            InFlightTask inFlightTask,
            SchedulerAction<?> action
    ) {
        this.scheduler = scheduler;
        this.inFlightTask = inFlightTask;
        this.action = action;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void run() {
        CancellationToken token = inFlightTask.getCancellationToken();
        if (token.isCancelled()) {
            scheduler.onTaskCancelled(inFlightTask);
            return;
        }

        Object result = null;
        Throwable error = null;
        try {
            result = action.execute(token);
        } catch (Throwable t) {
            error = t;
        }

        if (token.isCancelled()) {
            scheduler.onTaskCancelled(inFlightTask);
        } else if (error != null) {
            scheduler.onTaskFailed(inFlightTask, error);
        } else {
            scheduler.onTaskSucceeded(inFlightTask, result);
        }
    }
}
