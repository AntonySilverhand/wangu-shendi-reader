package org.wanshu.reader.data.repository;

import org.wanshu.reader.data.content.ContentDatabase;

/** Removes a shelf book (local TXT or online) with its cached text and TOC; personal data is kept. */
public class DeleteLocalBookTransactionRunnable implements Runnable {
    private final ContentDatabase db;
    private final String bookId;

    public DeleteLocalBookTransactionRunnable(ContentDatabase db, String bookId) {
        this.db = db;
        this.bookId = bookId;
    }

    @Override
    public void run() {
        int deleted = db.bookDao().deleteLocalBook(bookId);
        if (deleted == 0) deleted = db.bookDao().deleteOnlineBook(bookId);
        if (deleted > 0) {
            db.sourcePageDao().deletePagesForBook(bookId);
            db.chapterBlockDao().deleteBlocksForBook(bookId);
            db.chapterDao().deleteChaptersForBook(bookId);
            db.tocDao().deleteEntriesForBook(bookId);
            db.tocDao().deleteTocPagesForBook(bookId);
            db.downloadPolicyDao().deletePolicy(bookId);
            db.downloadTaskDao().clearTasksForBook(bookId);
        }
    }
}
