package org.wanshu.reader.data.repository;

import org.wanshu.reader.data.personal.PersonalDatabase;

public class ClearBookmarksRunnable implements Runnable {
    private final PersonalDatabase db;
    private final String bookId;
    private final CompletionCallback callback;

    public ClearBookmarksRunnable(
            PersonalDatabase db,
            String bookId,
            CompletionCallback callback
    ) {
        this.db = db;
        this.bookId = bookId != null ? bookId : "";
        this.callback = callback;
    }

    @Override
    public void run() {
        try {
            db.bookmarkDao().clearBookmarks(bookId);
            if (callback != null) {
                callback.onSuccess();
            }
        } catch (Throwable t) {
            if (callback != null) {
                callback.onError(t);
            }
        }
    }
}
