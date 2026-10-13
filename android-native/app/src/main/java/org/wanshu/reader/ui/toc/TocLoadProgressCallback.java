package org.wanshu.reader.ui.toc;

import org.wanshu.reader.data.AppExecutors;
import org.wanshu.reader.data.repository.TocLoadListener;

public final class TocLoadProgressCallback implements TocLoadListener {
    private final TocDialog dialog;
    private final AppExecutors executors;
    private final long generation;
    public TocLoadProgressCallback(TocDialog dialog, AppExecutors executors, long generation) {
        this.dialog = dialog; this.executors = executors; this.generation = generation;
    }
    @Override public void onProgress(int loaded, int total, boolean finished, String error) {
        executors.getMainThreadExecutor().execute(new TocProgressRunnable(dialog, loaded, total, finished, error, generation));
    }
}
