package org.wanshu.reader.data.repository;

import org.wanshu.reader.data.content.ContentDatabase;

public class ClearRemoteCacheRunnable implements Runnable {
    private final ContentDatabase db;
    private final String bookId;
    private final CompletionCallback callback;

    public ClearRemoteCacheRunnable(ContentDatabase db, String bookId, CompletionCallback callback) {
        this.db = db;
        this.bookId = bookId;
        this.callback = callback;
    }

    @Override
    public void run() {
        try {
            db.runInTransaction(new ClearRemoteCacheTransactionRunnable(db, bookId));
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
