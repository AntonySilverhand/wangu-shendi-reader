package org.wanshu.reader.core.queue;

public interface SchedulerCallback<T> {
    void onSuccess(T result);
    void onError(Throwable error);
    void onCancelled();
}
