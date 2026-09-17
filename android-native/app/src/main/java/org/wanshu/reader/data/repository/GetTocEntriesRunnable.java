package org.wanshu.reader.data.repository;

import java.util.List;
import org.wanshu.reader.data.content.ContentDatabase;
import org.wanshu.reader.data.content.entity.TocEntryEntity;

public class GetTocEntriesRunnable implements Runnable {
    private final ContentDatabase db;
    private final String bookId;
    private final DataCallback<List<TocEntryEntity>> callback;

    public GetTocEntriesRunnable(
            ContentDatabase db,
            String bookId,
            DataCallback<List<TocEntryEntity>> callback
    ) {
        this.db = db;
        this.bookId = bookId;
        this.callback = callback;
    }

    @Override
    public void run() {
        try {
            List<TocEntryEntity> entries = db.tocDao().getEntries(bookId);
            if (callback != null) {
                callback.onSuccess(entries);
            }
        } catch (Throwable t) {
            if (callback != null) {
                callback.onError(t);
            }
        }
    }
}
