package org.wanshu.reader.data.repository;

import org.wanshu.reader.data.personal.PersonalDatabase;

public class RecordHistoryRunnable implements Runnable {
    private final PersonalDatabase db;
    private final String bookId;
    private final String chapterId;
    private final String title;
    private final long readAt;
    private final CompletionCallback callback;

    public RecordHistoryRunnable(
            PersonalDatabase db,
            String bookId,
            String chapterId,
            String title,
            long readAt,
            CompletionCallback callback
    ) {
        this.db = db;
        this.bookId = bookId;
        this.chapterId = chapterId;
        this.title = title;
        this.readAt = readAt;
        this.callback = callback;
    }

    @Override
    public void run() {
        try {
            db.runInTransaction(new RecordHistoryTransactionRunnable(db, bookId, chapterId, title, readAt));
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
