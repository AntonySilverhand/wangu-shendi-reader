package org.wanshu.reader.data.repository;

import java.util.List;
import org.wanshu.reader.data.personal.PersonalDatabase;
import org.wanshu.reader.data.personal.entity.BookmarkEntity;

public class GetBookmarksRunnable implements Runnable {
    private final PersonalDatabase db;
    private final String bookId;
    private final DataCallback<List<BookmarkEntity>> callback;

    public GetBookmarksRunnable(
            PersonalDatabase db,
            String bookId,
            DataCallback<List<BookmarkEntity>> callback
    ) {
        this.db = db;
        this.bookId = bookId;
        this.callback = callback;
    }

    @Override
    public void run() {
        try {
            List<BookmarkEntity> list = db.bookmarkDao().getBookmarks(bookId);
            if (callback != null) {
                callback.onSuccess(list);
            }
        } catch (Throwable t) {
            if (callback != null) {
                callback.onError(t);
            }
        }
    }
}
