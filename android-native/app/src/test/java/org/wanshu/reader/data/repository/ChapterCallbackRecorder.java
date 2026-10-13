package org.wanshu.reader.data.repository;

import org.wanshu.reader.core.model.ChapterResult;

public final class ChapterCallbackRecorder implements DataCallback<ChapterResult> {
    int successes, errors;
    ChapterResult result;
    Throwable error;
    @Override public void onSuccess(ChapterResult value) { successes++; result = value; }
    @Override public void onError(Throwable value) { errors++; error = value; }
}
