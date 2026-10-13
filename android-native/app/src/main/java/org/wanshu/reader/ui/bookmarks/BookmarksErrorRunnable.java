package org.wanshu.reader.ui.bookmarks;

public class BookmarksErrorRunnable implements Runnable {
    private final BookmarksDialog dialog;
    private final String error;

    public BookmarksErrorRunnable(BookmarksDialog dialog, String error) {
        this.dialog = dialog;
        this.error = error;
    }

    @Override
    public void run() {
        if (dialog != null) {
            dialog.applyError(error);
        }
    }
}
