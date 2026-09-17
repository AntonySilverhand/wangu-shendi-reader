package org.wanshu.reader.data.repository;

import org.wanshu.reader.data.content.ContentDatabase;
import org.wanshu.reader.data.content.entity.BookEntity;

public class EnsureOnlineBookRunnable implements Runnable {
    private final ContentDatabase db;
    private final String bookId;
    private final String title;
    private final String author;
    private final CompletionCallback callback;

    public EnsureOnlineBookRunnable(
            ContentDatabase db,
            String bookId,
            String title,
            String author,
            CompletionCallback callback
    ) {
        this.db = db;
        this.bookId = bookId;
        this.title = title;
        this.author = author;
        this.callback = callback;
    }

    @Override
    public void run() {
        try {
            BookEntity book = db.bookDao().getBook(bookId);
            if (book == null) {
                book = new BookEntity();
                book.bookId = bookId;
                book.title = title;
                book.author = author;
                book.sourceType = "ONLINE";
                book.importStatus = "READY";
                book.createdAt = System.currentTimeMillis();
                book.updatedAt = System.currentTimeMillis();
                db.bookDao().insertBook(book);
            }
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
