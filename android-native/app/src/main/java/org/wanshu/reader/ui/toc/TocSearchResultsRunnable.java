package org.wanshu.reader.ui.toc;

public class TocSearchResultsRunnable implements Runnable {
    private final TocDialog dialog;

    public TocSearchResultsRunnable(TocDialog dialog) {
        this.dialog = dialog;
    }

    @Override
    public void run() {
        if (dialog != null) {
            dialog.applySearchResults();
        }
    }
}
