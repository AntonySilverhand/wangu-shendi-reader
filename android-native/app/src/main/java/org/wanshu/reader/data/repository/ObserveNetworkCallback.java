package org.wanshu.reader.data.repository;

import org.wanshu.reader.core.model.ChapterResult;

public class ObserveNetworkCallback implements DataCallback<ChapterResult> {

    private final ReaderRepository repository;
    private final long generation;
    private final ReaderObserver observer;

    public ObserveNetworkCallback(
            ReaderRepository repository,
            long generation,
            ReaderObserver observer
    ) {
        this.repository = repository;
        this.generation = generation;
        this.observer = observer;
    }

    @Override
    public void onSuccess(ChapterResult result) {
        if (observer != null && repository.isCurrentGeneration(generation)) {
            observer.onCompleteLoaded(result);
        }
    }

    @Override
    public void onError(Throwable error) {
        if (observer != null && repository.isCurrentGeneration(generation)) {
            observer.onError(error);
        }
    }
}
