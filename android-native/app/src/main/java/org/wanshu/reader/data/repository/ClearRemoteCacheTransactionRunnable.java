package org.wanshu.reader.data.repository;

import org.wanshu.reader.data.content.ContentDatabase;

public class ClearRemoteCacheTransactionRunnable implements Runnable {
    private final ContentDatabase db;
    private final String bookId;

    public ClearRemoteCacheTransactionRunnable(ContentDatabase db, String bookId) {
        this.db = db;
        this.bookId = bookId;
    }

    @Override
    public void run() {
        db.chapterBlockDao().clearRemoteBlocks(bookId);
        db.chapterDao().clearRemoteCache(bookId);
        db.sourcePageDao().deletePagesForBook(bookId);
        db.downloadTaskDao().clearTasksForBook(bookId);
    }
}
