package org.wanshu.reader.data.repository;

import java.util.concurrent.atomic.AtomicLong;
import org.wanshu.reader.core.model.ChapterResult;
import org.wanshu.reader.core.net.SourceHttpClient;
import org.wanshu.reader.core.queue.RequestPriority;
import org.wanshu.reader.core.queue.RequestScheduler;
import org.wanshu.reader.core.util.Base64Decoder;

public class ReaderRepository {

    private final ContentRepository contentRepository;
    private final RequestScheduler scheduler;
    private final SourceHttpClient httpClient;
    private final Base64Decoder decoder;
    private final AtomicLong currentGeneration = new AtomicLong(1L);

    public ReaderRepository(
            ContentRepository contentRepository,
            RequestScheduler scheduler,
            SourceHttpClient httpClient,
            Base64Decoder decoder
    ) {
        this.contentRepository = contentRepository;
        this.scheduler = scheduler;
        this.httpClient = httpClient != null ? httpClient : new SourceHttpClient();
        this.decoder = decoder;
    }

    public long incrementGeneration() {
        return currentGeneration.incrementAndGet();
    }

    public boolean isCurrentGeneration(long generation) {
        return currentGeneration.get() == generation;
    }

    public void readCached(String bookId, String chapterId, DataCallback<ChapterResult> callback) {
        contentRepository.getChapter(bookId, chapterId, callback);
    }

    public void ensureComplete(
            String bookId,
            String chapterId,
            RequestPriority priority,
            Object subscriberId,
            DataCallback<ChapterResult> callback
    ) {
        EnsureCompleteCacheCheckCallback cacheCheck = new EnsureCompleteCacheCheckCallback(
                bookId,
                chapterId,
                priority,
                subscriberId,
                callback,
                contentRepository,
                scheduler,
                httpClient,
                decoder
        );
        contentRepository.getChapter(bookId, chapterId, cacheCheck);
    }

    public long observe(
            String bookId,
            String chapterId,
            Object subscriberId,
            ReaderObserver observer
    ) {
        long gen = incrementGeneration();
        ObserveCacheCallback cacheCb = new ObserveCacheCallback(
                this,
                bookId,
                chapterId,
                gen,
                subscriberId,
                observer
        );
        contentRepository.getChapter(bookId, chapterId, cacheCb);
        return gen;
    }

    public void cancel(Object subscriberId) {
        if (subscriberId != null) {
            scheduler.cancelAllForSubscriber(subscriberId);
        }
    }

    public ContentRepository getContentRepository() {
        return contentRepository;
    }

    public SourceHttpClient getHttpClient() {
        return httpClient;
    }
}
