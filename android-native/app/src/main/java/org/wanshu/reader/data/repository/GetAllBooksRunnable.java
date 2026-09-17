package org.wanshu.reader.data.repository;

import java.util.List;
import org.wanshu.reader.data.content.ContentDatabase;
import org.wanshu.reader.data.content.entity.BookEntity;

public class GetAllBooksRunnable implements Runnable {
    private final ContentDatabase db;
    private final DataCallback<List<BookEntity>> callback;

    public GetAllBooksRunnable(ContentDatabase db, DataCallback<List<BookEntity>> callback) {
        this.db = db;
        this.callback = callback;
    }

    @Override
    public void run() {
        try {
            List<BookEntity> books = db.bookDao().getAllBooks();
            if (callback != null) {
                callback.onSuccess(books);
            }
        } catch (Throwable t) {
            if (callback != null) {
                callback.onError(t);
            }
        }
    }
}
