package org.wanshu.reader.data.repository;

public interface DataCallback<T> {
    void onSuccess(T result);
    void onError(Throwable error);
}
