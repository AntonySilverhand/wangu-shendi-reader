package org.wanshu.reader.data.repository;

import org.wanshu.reader.data.personal.PersonalDatabase;
import org.wanshu.reader.data.personal.entity.ReadingHistoryEntity;

public class RecordHistoryTransactionRunnable implements Runnable {
    private final PersonalDatabase db;
    private final String bookId;
    private final String chapterId;
    private final String title;
    private final long readAt;

    public RecordHistoryTransactionRunnable(
            PersonalDatabase db,
            String bookId,
            String chapterId,
            String title,
            long readAt
    ) {
        this.db = db;
        this.bookId = bookId;
        this.chapterId = chapterId;
        this.title = title;
        this.readAt = readAt;
    }

    @Override
    public void run() {
        db.readingHistoryDao().deleteEntry(bookId, chapterId);

        ReadingHistoryEntity entity = new ReadingHistoryEntity();
        entity.bookId = bookId;
        entity.chapterId = chapterId;
        entity.title = title != null ? title : "";
        entity.readAt = readAt;
        db.readingHistoryDao().insertHistory(entity);

        db.readingHistoryDao().trimHistory(bookId);
    }
}
