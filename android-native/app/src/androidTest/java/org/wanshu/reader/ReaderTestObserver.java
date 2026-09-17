package org.wanshu.reader;

import java.util.concurrent.CountDownLatch;
import org.wanshu.reader.core.model.ChapterResult;
import org.wanshu.reader.data.repository.ReaderObserver;

public class ReaderTestObserver implements ReaderObserver {
    private final CountDownLatch cachedLatch;
    private final CountDownLatch completeLatch;
    private ChapterResult cachedResult;
    private ChapterResult completeResult;
    private Throwable error;

    public ReaderTestObserver(CountDownLatch cachedLatch, CountDownLatch completeLatch) {
        this.cachedLatch = cachedLatch;
        this.completeLatch = completeLatch;
    }

    @Override
    public void onCachedLoaded(ChapterResult cached) {
        this.cachedResult = cached;
        if (cachedLatch != null) {
            cachedLatch.countDown();
        }
    }

    @Override
    public void onCompleteLoaded(ChapterResult complete) {
        this.completeResult = complete;
        if (completeLatch != null) {
            completeLatch.countDown();
        }
    }

    @Override
    public void onError(Throwable error) {
        this.error = error;
        if (completeLatch != null) {
            completeLatch.countDown();
        }
    }

    public ChapterResult getCachedResult() {
        return cachedResult;
    }

    public ChapterResult getCompleteResult() {
        return completeResult;
    }

    public Throwable getError() {
        return error;
    }
}
