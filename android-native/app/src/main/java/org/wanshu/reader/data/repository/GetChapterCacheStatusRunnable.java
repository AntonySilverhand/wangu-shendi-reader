package org.wanshu.reader.data.repository;

import java.util.List;
import org.wanshu.reader.data.content.ChapterCacheStatus;
import org.wanshu.reader.data.content.ContentDatabase;

public class GetChapterCacheStatusRunnable implements Runnable {
    private final ContentDatabase db;
    private final String bookId;
    private final DataCallback<ChapterCacheStatus> callback;

    public GetChapterCacheStatusRunnable(
            ContentDatabase db,
            String bookId,
            DataCallback<ChapterCacheStatus> callback
    ) {
        this.db = db;
        this.bookId = bookId;
        this.callback = callback;
    }

    @Override
    public void run() {
        try {
            List<String> completed = db.chapterDao().getCompletedChapterIds(bookId);
            List<String> allCached = db.chapterDao().getAllCachedChapterIds(bookId);
            ChapterCacheStatus status = new ChapterCacheStatus(completed, allCached);
            if (callback != null) {
                callback.onSuccess(status);
            }
        } catch (Exception e) {
            if (callback != null) {
                callback.onError(e);
            }
        }
    }
}
