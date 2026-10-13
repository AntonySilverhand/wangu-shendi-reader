package org.wanshu.reader.ui.bookmarks;

public class BookmarksReloadRunnable implements Runnable {
    private final BookmarksDialog dialog;

    public BookmarksReloadRunnable(BookmarksDialog dialog) {
        this.dialog = dialog;
    }

    @Override
    public void run() {
        if (dialog != null && dialog.isShowing()) {
            dialog.loadData();
        }
    }
}
