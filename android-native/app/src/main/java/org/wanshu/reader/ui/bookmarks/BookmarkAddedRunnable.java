package org.wanshu.reader.ui.bookmarks;

import org.wanshu.reader.data.personal.entity.BookmarkEntity;

public class BookmarkAddedRunnable implements Runnable {
    private final BookmarksDialog dialog;
    private final BookmarkEntity bookmark;

    public BookmarkAddedRunnable(BookmarksDialog dialog, BookmarkEntity bookmark) {
        this.dialog = dialog;
        this.bookmark = bookmark;
    }

    @Override
    public void run() {
        if (dialog != null && bookmark != null) {
            dialog.onBookmarkAdded(bookmark);
        }
    }
}
