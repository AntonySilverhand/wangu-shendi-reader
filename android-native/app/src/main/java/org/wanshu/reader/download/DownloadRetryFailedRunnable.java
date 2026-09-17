package org.wanshu.reader.download;

import org.wanshu.reader.data.content.ContentDatabase;

public class DownloadRetryFailedRunnable implements Runnable {
    private final ContentDatabase db;
    private final String bookId;
    private final DownloadCoordinator coordinator;

    public DownloadRetryFailedRunnable(ContentDatabase db, String bookId, DownloadCoordinator coordinator) {
        this.db = db;
        this.bookId = bookId != null ? bookId : "";
        this.coordinator = coordinator;
    }

    @Override
    public void run() {
        if (db != null && !bookId.isEmpty()) {
            db.downloadTaskDao().resetFailedTasks(bookId);
        }
        if (coordinator != null) {
            coordinator.notifyProgress();
            coordinator.pump();
        }
    }
}
