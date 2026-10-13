package org.wanshu.reader.ui.toc;

public class TocLoadErrorRunnable implements Runnable {
    private final TocDialog dialog;
    private final String error;

    public TocLoadErrorRunnable(TocDialog dialog, String error) {
        this.dialog = dialog;
        this.error = error;
    }

    @Override
    public void run() {
        if (dialog != null) {
            dialog.applyLoadError(error);
        }
    }
}
