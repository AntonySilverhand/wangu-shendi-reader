package org.wanshu.reader.ui.shelf;

public class ShelfImportProgressRunnable implements Runnable {
    private final ShelfView view;
    private final String message;

    public ShelfImportProgressRunnable(ShelfView view, String message) {
        this.view = view;
        this.message = message;
    }

    @Override
    public void run() {
        if (message == null) {
            view.hideImportProgress();
        } else {
            view.showImportProgress(message);
        }
    }
}
