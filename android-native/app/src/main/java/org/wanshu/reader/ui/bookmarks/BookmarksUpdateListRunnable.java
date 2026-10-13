package org.wanshu.reader.ui.bookmarks;

import java.util.List;
import org.wanshu.reader.data.personal.entity.BookmarkEntity;

public class BookmarksUpdateListRunnable implements Runnable {
    private final BookmarksDialog dialog;
    private final List<BookmarkEntity> bookmarks;

    public BookmarksUpdateListRunnable(BookmarksDialog dialog, List<BookmarkEntity> bookmarks) {
        this.dialog = dialog;
        this.bookmarks = bookmarks;
    }

    @Override
    public void run() {
        if (dialog != null) {
            dialog.applyBookmarksList(bookmarks);
        }
    }
}
