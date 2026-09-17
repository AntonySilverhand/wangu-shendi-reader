package org.wanshu.reader.data.repository;

import java.util.concurrent.CancellationException;
import org.wanshu.reader.core.model.ChapterResult;
import org.wanshu.reader.core.queue.SchedulerCallback;

public class EnsureCompleteSchedulerCallback implements SchedulerCallback<ChapterResult> {

    private final ContentRepository contentRepository;
    private final DataCallback<ChapterResult> userCallback;

    public EnsureCompleteSchedulerCallback(
            ContentRepository contentRepository,
            DataCallback<ChapterResult> userCallback
    ) {
        this.contentRepository = contentRepository;
        this.userCallback = userCallback;
    }

    @Override
    public void onSuccess(ChapterResult result) {
        if (result != null) {
            contentRepository.saveChapter(result, "REMOTE", new EnsureCompleteSaveCallback(result, userCallback));
        } else {
            if (userCallback != null) {
                userCallback.onError(new IllegalStateException("Fetched chapter is null"));
            }
        }
    }

    @Override
    public void onError(Throwable error) {
        if (userCallback != null) {
            userCallback.onError(error);
        }
    }

    @Override
    public void onCancelled() {
        if (userCallback != null) {
            userCallback.onError(new CancellationException("Request was cancelled"));
        }
    }
}
