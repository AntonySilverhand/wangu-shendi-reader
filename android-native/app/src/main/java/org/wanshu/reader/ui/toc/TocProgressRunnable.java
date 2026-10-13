package org.wanshu.reader.ui.toc;

public final class TocProgressRunnable implements Runnable {
    private final TocDialog dialog;
    private final int loaded, total;
    private final boolean finished;
    private final String error;
    private final long generation;
    public TocProgressRunnable(TocDialog dialog, int loaded, int total, boolean finished, String error, long generation) {
        this.dialog = dialog; this.loaded = loaded; this.total = total; this.finished = finished; this.error = error; this.generation = generation;
    }
    @Override public void run() { dialog.applyLoadProgress(loaded, total, finished, error, generation); }
}
