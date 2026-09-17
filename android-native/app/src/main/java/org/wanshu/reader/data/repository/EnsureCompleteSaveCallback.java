package org.wanshu.reader.data.repository;

import org.wanshu.reader.core.model.ChapterResult;

public class EnsureCompleteSaveCallback implements CompletionCallback {

    private final ChapterResult result;
    private final DataCallback<ChapterResult> userCallback;

    public EnsureCompleteSaveCallback(ChapterResult result, DataCallback<ChapterResult> userCallback) {
        this.result = result;
        this.userCallback = userCallback;
    }

    @Override
    public void onSuccess() {
        if (userCallback != null) {
            userCallback.onSuccess(result);
        }
    }

    @Override
    public void onError(Throwable error) {
        if (userCallback != null) {
            // Even if save failed, deliver the in-memory parsed result
            userCallback.onSuccess(result);
        }
    }
}
