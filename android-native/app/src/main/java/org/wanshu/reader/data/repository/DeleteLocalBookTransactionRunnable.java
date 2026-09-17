package org.wanshu.reader.data.repository;

import org.wanshu.reader.data.content.ContentDatabase;

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
        if (deleted > 0) {
            db.chapterBlockDao().deleteBlocksForBook(bookId);
            db.chapterDao().deleteChaptersForBook(bookId);
            db.tocDao().deleteEntriesForBook(bookId);
            db.tocDao().deleteTocPagesForBook(bookId);
            db.downloadPolicyDao().deletePolicy(bookId);
            db.downloadTaskDao().clearTasksForBook(bookId);
        }
    }
}
