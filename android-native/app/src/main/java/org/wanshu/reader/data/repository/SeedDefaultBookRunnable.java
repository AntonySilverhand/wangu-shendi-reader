package org.wanshu.reader.data.repository;

import android.content.SharedPreferences;
import org.wanshu.reader.core.source.SourceUrls;
import org.wanshu.reader.data.content.ContentDatabase;
import org.wanshu.reader.data.content.entity.BookEntity;

/**
 * Puts the default book on the shelf exactly once. After the flag is set the book is never added again,
 * so removing it from the shelf sticks. Runs on the content executor (the preference read blocks).
 */
public class SeedDefaultBookRunnable implements Runnable {
    public static final String PREF_SEEDED = "default_book_seeded";
    public static final String DEFAULT_TITLE = "万古神帝";
    public static final String DEFAULT_AUTHOR = "飞天鱼";

    private final ContentDatabase db;
    private final SharedPreferences prefs;
    private final CompletionCallback callback;

    public SeedDefaultBookRunnable(ContentDatabase db, SharedPreferences prefs, CompletionCallback callback) {
        this.db = db;
        this.prefs = prefs;
        this.callback = callback;
    }

    @Override
    public void run() {
        try {
            if (!prefs.getBoolean(PREF_SEEDED, false)) {
                if (db.bookDao().getBook(SourceUrls.DEFAULT_BOOK_ID) == null) {
                    BookEntity book = new BookEntity();
                    book.bookId = SourceUrls.DEFAULT_BOOK_ID;
                    book.title = DEFAULT_TITLE;
                    book.author = DEFAULT_AUTHOR;
                    book.sourceType = "ONLINE";
                    book.importStatus = "READY";
                    book.createdAt = System.currentTimeMillis();
                    book.updatedAt = book.createdAt;
                    db.bookDao().insertBook(book);
                }
                prefs.edit().putBoolean(PREF_SEEDED, true).commit();
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
