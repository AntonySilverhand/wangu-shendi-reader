package org.wanshu.reader.ui.toc;

public class TocBuildItemsRunnable implements Runnable {
    private final TocDialog dialog;

    public TocBuildItemsRunnable(TocDialog dialog) {
        this.dialog = dialog;
    }

    @Override
    public void run() {
        if (dialog != null) {
            dialog.buildAndDisplayItems();
        }
    }
}
