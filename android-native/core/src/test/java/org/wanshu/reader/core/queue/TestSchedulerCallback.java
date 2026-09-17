package org.wanshu.reader.core.queue;

public class TestSchedulerCallback implements SchedulerCallback<String> {

    private String result = null;
    private Throwable error = null;
    private boolean cancelled = false;

    @Override
    public void onSuccess(String result) {
        this.result = result;
    }

    @Override
    public void onError(Throwable error) {
        this.error = error;
    }

    @Override
    public void onCancelled() {
        this.cancelled = true;
    }

    public String getResult() {
        return result;
    }

    public Throwable getError() {
        return error;
    }

    public boolean isCancelled() {
        return cancelled;
    }
}
