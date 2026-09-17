package org.wanshu.reader.download;

import org.wanshu.reader.data.content.ContentDatabase;

public class DownloadResetOrphanedRunnable implements Runnable {
    private final ContentDatabase db;
    private final DownloadCoordinator coordinator;

    public DownloadResetOrphanedRunnable(ContentDatabase db, DownloadCoordinator coordinator) {
        this.db = db;
        this.coordinator = coordinator;
    }

    @Override
    public void run() {
        if (db != null) {
            db.downloadTaskDao().resetOrphanedRunningTasks();
        }
        if (coordinator != null) {
            coordinator.pump();
        }
    }
}
