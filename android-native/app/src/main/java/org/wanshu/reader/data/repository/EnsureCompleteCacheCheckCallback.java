package org.wanshu.reader.data.repository;

import org.wanshu.reader.core.model.ChapterResult;
import org.wanshu.reader.core.net.SourceHttpClient;
import org.wanshu.reader.core.queue.RequestKey;
import org.wanshu.reader.core.queue.RequestPriority;
import org.wanshu.reader.core.queue.RequestScheduler;
import org.wanshu.reader.core.util.Base64Decoder;

public class EnsureCompleteCacheCheckCallback implements DataCallback<ChapterResult> {

    private final String bookId;
    private final String chapterId;
    private final RequestPriority priority;
    private final Object subscriberId;
    private final DataCallback<ChapterResult> userCallback;
    private final ContentRepository contentRepository;
    private final RequestScheduler scheduler;
    private final SourceHttpClient httpClient;
    private final Base64Decoder decoder;

    public EnsureCompleteCacheCheckCallback(
            String bookId,
            String chapterId,
            RequestPriority priority,
            Object subscriberId,
            DataCallback<ChapterResult> userCallback,
            ContentRepository contentRepository,
            RequestScheduler scheduler,
            SourceHttpClient httpClient,
            Base64Decoder decoder
    ) {
        this.bookId = bookId;
        this.chapterId = chapterId;
        this.priority = priority;
        this.subscriberId = subscriberId;
        this.userCallback = userCallback;
        this.contentRepository = contentRepository;
        this.scheduler = scheduler;
        this.httpClient = httpClient;
        this.decoder = decoder;
    }

    @Override
    public void onSuccess(ChapterResult cached) {
        if (cached != null && cached.isComplete()) {
            if (userCallback != null) {
                userCallback.onSuccess(cached);
            }
            return;
        }

        // Cache missing or incomplete, enqueue network fetch
        RequestKey key = RequestKey.chapterAll(bookId, chapterId);
        FetchChapterSchedulerAction action = new FetchChapterSchedulerAction(bookId, chapterId, httpClient, decoder);
        EnsureCompleteSchedulerCallback callback = new EnsureCompleteSchedulerCallback(contentRepository, userCallback);
        scheduler.enqueue(key, priority, subscriberId, action, callback);
    }

    @Override
    public void onError(Throwable error) {
        // Cache read failed, fallback to network
        RequestKey key = RequestKey.chapterAll(bookId, chapterId);
        FetchChapterSchedulerAction action = new FetchChapterSchedulerAction(bookId, chapterId, httpClient, decoder);
        EnsureCompleteSchedulerCallback callback = new EnsureCompleteSchedulerCallback(contentRepository, userCallback);
        scheduler.enqueue(key, priority, subscriberId, action, callback);
    }
}
