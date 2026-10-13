package org.wanshu.reader.core.toc;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Holds one search open so cancellation and input-thread work can be tested deterministically. */
public final class BlockingTocSearchDeps extends FakeTocSearchDeps {
    private final Thread inputThread = Thread.currentThread();
    final CountDownLatch started = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);

    public BlockingTocSearchDeps(FakeTocStore store) { super(store); }

    @Override public List<TocSearchResult> searchLoaded(String query, int limit) {
        if (Thread.currentThread() == inputThread) throw new AssertionError("Search ran on input thread");
        started.countDown();
        try {
            if (!release.await(3, TimeUnit.SECONDS)) throw new AssertionError("Search not released");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        }
        return super.searchLoaded(query, limit);
    }
}
