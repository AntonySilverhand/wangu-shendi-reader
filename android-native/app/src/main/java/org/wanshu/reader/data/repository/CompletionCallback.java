package org.wanshu.reader.data.repository;

public interface CompletionCallback {
    void onSuccess();
    void onError(Throwable error);
}
