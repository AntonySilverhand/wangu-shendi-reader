package org.wanshu.reader.data.repository;

import org.wanshu.reader.core.model.ReadingAnchor;
import org.wanshu.reader.data.personal.PersonalDatabase;
import org.wanshu.reader.data.personal.entity.ReadingProgressEntity;

public class GetProgressRunnable implements Runnable {
    private final PersonalDatabase db;
    private final String bookId;
    private final DataCallback<ReadingAnchor> callback;

    public GetProgressRunnable(
            PersonalDatabase db,
            String bookId,
            DataCallback<ReadingAnchor> callback
    ) {
        this.db = db;
        this.bookId = bookId;
        this.callback = callback;
    }

    @Override
    public void run() {
        try {
            ReadingProgressEntity entity = db.readingProgressDao().getProgress(bookId);
            if (entity == null) {
                if (callback != null) {
                    callback.onSuccess(null);
                }
                return;
            }

            ReadingAnchor anchor = new ReadingAnchor(
                    entity.bookId,
                    entity.chapterId,
                    entity.paragraphIndex,
                    entity.offsetUtf16,
                    entity.updatedAt,
                    entity.paragraphHash,
                    entity.quote,
                    entity.contentRevision
            );

            if (callback != null) {
                callback.onSuccess(anchor);
            }
        } catch (Throwable t) {
            if (callback != null) {
                callback.onError(t);
            }
        }
    }
}
