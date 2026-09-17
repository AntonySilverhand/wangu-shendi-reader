package org.wanshu.reader.data.repository;

import org.wanshu.reader.data.personal.PersonalDatabase;

public class DeleteBookmarkRunnable implements Runnable {
    private final PersonalDatabase db;
    private final String bookmarkId;
    private final CompletionCallback callback;

    public DeleteBookmarkRunnable(PersonalDatabase db, String bookmarkId, CompletionCallback callback) {
        this.db = db;
        this.bookmarkId = bookmarkId;
        this.callback = callback;
    }

    @Override
    public void run() {
        try {
            db.bookmarkDao().deleteBookmark(bookmarkId);
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
