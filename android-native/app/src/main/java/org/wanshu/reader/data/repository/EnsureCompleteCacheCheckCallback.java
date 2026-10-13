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
    private final org.wanshu.reader.core.net.CancellationToken scope;

    public EnsureCompleteCacheCheckCallback(
            String bookId,
            String chapterId,
            RequestPriority priority,
            Object subscriberId,
            DataCallback<ChapterResult> userCallback,
            ContentRepository contentRepository,
            RequestScheduler scheduler,
            SourceHttpClient httpClient,
            Base64Decoder decoder,
            org.wanshu.reader.core.net.CancellationToken scope
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
        this.scope = scope;
    }

    @Override
    public void onSuccess(ChapterResult cached) {
        if (scope.isCancelled()) return;
        if (!"36780".equals(bookId)) {
            if (userCallback != null) {
                if (cached != null) userCallback.onSuccess(cached);
                else userCallback.onError(new java.io.IOException("本地章节不存在，请重新导入原 TXT；不会联网获取同名章节"));
            }
            return;
        }
        if (cached != null && cached.isComplete()) {
            if (userCallback != null) {
                userCallback.onSuccess(cached);
            }
            return;
        }

        fetchRemote();
    }

    @Override
    public void onError(Throwable error) {
        if (scope.isCancelled()) return;
        if (!"36780".equals(bookId)) {
            if (userCallback != null) userCallback.onError(error);
            return;
        }
        fetchRemote();
    }

    private void fetchRemote() {
        RequestKey key = RequestKey.chapterAll(bookId, chapterId);
        FetchChapterSchedulerAction action = new FetchChapterSchedulerAction(bookId, chapterId, httpClient, decoder);
        EnsureCompleteSchedulerCallback callback = new EnsureCompleteSchedulerCallback(contentRepository, userCallback);
        synchronized (scope) {
            if (!scope.isCancelled()) scheduler.enqueue(key, priority, subscriberId, action, callback);
        }
    }
}
