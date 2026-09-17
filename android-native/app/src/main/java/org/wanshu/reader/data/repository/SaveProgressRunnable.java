package org.wanshu.reader.data.repository;

import org.wanshu.reader.core.model.ReadingAnchor;
import org.wanshu.reader.data.personal.PersonalDatabase;
import org.wanshu.reader.data.personal.entity.ReadingProgressEntity;

public class SaveProgressRunnable implements Runnable {
    private final PersonalDatabase db;
    private final ReadingAnchor anchor;
    private final long sequenceNumber;
    private final CompletionCallback callback;

    public SaveProgressRunnable(
            PersonalDatabase db,
            ReadingAnchor anchor,
            long sequenceNumber,
            CompletionCallback callback
    ) {
        this.db = db;
        this.anchor = anchor;
        this.sequenceNumber = sequenceNumber;
        this.callback = callback;
    }

    @Override
    public void run() {
        try {
            ReadingProgressEntity existing = db.readingProgressDao().getProgress(anchor.getBookId());
            if (existing != null && existing.sequenceNumber > sequenceNumber) {
                // Stale write, ignore
                if (callback != null) {
                    callback.onSuccess();
                }
                return;
            }

            ReadingProgressEntity entity = new ReadingProgressEntity();
            entity.bookId = anchor.getBookId();
            entity.chapterId = anchor.getChapterId();
            entity.paragraphIndex = anchor.getParagraphIndex();
            entity.offsetUtf16 = anchor.getOffsetUtf16();
            entity.updatedAt = anchor.getUpdatedAt() > 0 ? anchor.getUpdatedAt() : System.currentTimeMillis();
            entity.sequenceNumber = sequenceNumber;
            entity.paragraphHash = anchor.getParagraphHash();
            entity.quote = anchor.getQuote();
            entity.contentRevision = anchor.getContentRevision();

            db.readingProgressDao().saveProgress(entity);

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
