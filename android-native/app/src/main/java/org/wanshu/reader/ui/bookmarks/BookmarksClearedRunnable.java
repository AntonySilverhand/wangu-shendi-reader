package org.wanshu.reader.ui.bookmarks;

public class BookmarksClearedRunnable implements Runnable {
    private final BookmarksDialog dialog;

    public BookmarksClearedRunnable(BookmarksDialog dialog) {
        this.dialog = dialog;
    }

    @Override
    public void run() {
        if (dialog != null) {
            dialog.onBookmarksCleared();
        }
    }
}
