package org.wanshu.reader.ui.toc;

public class TocLoadAllRunnable implements Runnable {
    private final ContentTocSearchDeps deps;
    private final TocDataLoaderRunnable refreshRunnable;
    private final java.util.concurrent.Executor dbExecutor;

    public TocLoadAllRunnable(
            ContentTocSearchDeps deps,
            TocDataLoaderRunnable refreshRunnable,
            java.util.concurrent.Executor dbExecutor
    ) {
        this.deps = deps;
        this.refreshRunnable = refreshRunnable;
        this.dbExecutor = dbExecutor;
    }

    @Override
    public void run() {
        if (deps != null) {
            try {
                deps.loadRange(1, deps.totalPages(), null);
            } catch (Exception ignored) {
            }
        }
        if (dbExecutor != null && refreshRunnable != null) {
            dbExecutor.execute(refreshRunnable);
        }
    }
}
