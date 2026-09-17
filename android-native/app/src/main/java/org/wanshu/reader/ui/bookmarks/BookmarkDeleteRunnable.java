package org.wanshu.reader.ui.bookmarks;

public class BookmarkDeleteRunnable implements Runnable {
    private final BookmarksDialog dialog;
    private final String bookmarkId;

    public BookmarkDeleteRunnable(BookmarksDialog dialog, String bookmarkId) {
        this.dialog = dialog;
        this.bookmarkId = bookmarkId;
    }

    @Override
    public void run() {
        if (dialog != null) {
            dialog.onBookmarkDeleted(bookmarkId);
        }
    }
}
