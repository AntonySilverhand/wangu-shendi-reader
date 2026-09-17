package org.wanshu.reader.data.repository;

import org.wanshu.reader.data.content.ContentDatabase;
import org.wanshu.reader.data.content.StorageStats;

public class GetStorageStatsRunnable implements Runnable {
    private final ContentDatabase db;
    private final String bookId;
    private final DataCallback<StorageStats> callback;

    public GetStorageStatsRunnable(
            ContentDatabase db,
            String bookId,
            DataCallback<StorageStats> callback
    ) {
        this.db = db;
        this.bookId = bookId;
        this.callback = callback;
    }

    @Override
    public void run() {
        try {
            int completed = db.chapterDao().getCompletedCount(bookId);
            int partial = db.chapterDao().getPartialCount(bookId);
            long bytes = db.chapterDao().getTotalBytes(bookId);
            StorageStats stats = new StorageStats(completed, partial, bytes);
            if (callback != null) {
                callback.onSuccess(stats);
            }
        } catch (Throwable t) {
            if (callback != null) {
                callback.onError(t);
            }
        }
    }
}
