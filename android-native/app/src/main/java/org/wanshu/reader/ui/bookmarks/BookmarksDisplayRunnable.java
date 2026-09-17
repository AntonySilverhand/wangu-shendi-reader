package org.wanshu.reader.ui.bookmarks;

import java.util.List;
import org.wanshu.reader.data.personal.entity.BookmarkEntity;

public class BookmarksDisplayRunnable implements Runnable {
    private final BookmarksDialog dialog;
    private final List<BookmarkEntity> bookmarks;

    public BookmarksDisplayRunnable(BookmarksDialog dialog, List<BookmarkEntity> bookmarks) {
        this.dialog = dialog;
        this.bookmarks = bookmarks;
    }

    @Override
    public void run() {
        if (dialog != null) {
            dialog.onBookmarksLoaded(bookmarks);
        }
    }
}
