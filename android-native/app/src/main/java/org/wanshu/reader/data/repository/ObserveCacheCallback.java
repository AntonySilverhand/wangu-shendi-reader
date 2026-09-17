package org.wanshu.reader.data.repository;

import org.wanshu.reader.core.model.ChapterResult;
import org.wanshu.reader.core.queue.RequestPriority;

public class ObserveCacheCallback implements DataCallback<ChapterResult> {

    private final ReaderRepository repository;
    private final String bookId;
    private final String chapterId;
    private final long generation;
    private final Object subscriberId;
    private final ReaderObserver observer;

    public ObserveCacheCallback(
            ReaderRepository repository,
            String bookId,
            String chapterId,
            long generation,
            Object subscriberId,
            ReaderObserver observer
    ) {
        this.repository = repository;
        this.bookId = bookId;
        this.chapterId = chapterId;
        this.generation = generation;
        this.subscriberId = subscriberId;
        this.observer = observer;
    }

    @Override
    public void onSuccess(ChapterResult cached) {
        if (!repository.isCurrentGeneration(generation)) {
            return;
        }

        if (cached != null) {
            // Immediately deliver cached chapter for zero-latency reading
            if (observer != null) {
                observer.onCachedLoaded(cached);
            }

            // If already complete, we do not need network fetch
            if (cached.isComplete()) {
                return;
            }
        }

        // Trigger background completion
        ObserveNetworkCallback netCb = new ObserveNetworkCallback(repository, generation, observer);
        repository.ensureComplete(bookId, chapterId, RequestPriority.HIGH, subscriberId, netCb);
    }

    @Override
    public void onError(Throwable error) {
        if (!repository.isCurrentGeneration(generation)) {
            return;
        }

        // Cache error, attempt network fetch directly
        ObserveNetworkCallback netCb = new ObserveNetworkCallback(repository, generation, observer);
        repository.ensureComplete(bookId, chapterId, RequestPriority.HIGH, subscriberId, netCb);
    }
}
