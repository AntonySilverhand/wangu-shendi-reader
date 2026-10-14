package org.wanshu.reader.data.repository;

import org.wanshu.reader.core.model.BookKey;
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
            if (!BookKey.isOnlineId(bookId)) {
                throw new IllegalArgumentException("not an online book id: " + bookId);
            }
            BookEntity book = db.bookDao().getBook(bookId);
            if (book == null) {
                book = new BookEntity();
                book.bookId = bookId;
                book.title = title != null ? title.trim() : "";
                book.author = author != null ? author.trim() : "";
                book.sourceType = "ONLINE";
                book.importStatus = "READY";
                book.createdAt = System.currentTimeMillis();
                book.updatedAt = System.currentTimeMillis();
                db.bookDao().insertBook(book);
            } else {
                // Known book: take over better metadata (e.g. from search), never blank a field.
                boolean changed = false;
                if (title != null && !title.trim().isEmpty() && !title.equals(book.title)) {
                    book.title = title.trim();
                    changed = true;
                }
                if (author != null && !author.trim().isEmpty() && !author.equals(book.author)) {
                    book.author = author.trim();
                    changed = true;
                }
                if (changed) {
                    book.updatedAt = System.currentTimeMillis();
                    db.bookDao().updateBook(book);
                }
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
