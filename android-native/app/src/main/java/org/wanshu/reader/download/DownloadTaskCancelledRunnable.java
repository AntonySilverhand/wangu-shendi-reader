package org.wanshu.reader.download;

import org.wanshu.reader.core.download.DownloadTaskState;
import org.wanshu.reader.data.content.ContentDatabase;
import org.wanshu.reader.data.content.entity.DownloadTaskEntity;

public class DownloadTaskCancelledRunnable implements Runnable {
    private final DownloadCoordinator coordinator;
    private final ContentDatabase db;
    private final DownloadTaskEntity task;

    public DownloadTaskCancelledRunnable(
            DownloadCoordinator coordinator,
            ContentDatabase db,
            DownloadTaskEntity task
    ) {
        this.coordinator = coordinator;
        this.db = db;
        this.task = task;
    }

    @Override
    public void run() {
        if (task != null && db != null) {
            // Revert RUNNING to PENDING without penalty
            db.downloadTaskDao().updateTaskState(
                    task.bookId,
                    task.chapterId,
                    DownloadTaskState.PENDING,
                    task.attempts,
                    task.nextAttemptAt,
                    task.errorType,
                    0L
            );
        }

        if (coordinator != null) {
            coordinator.notifyProgress();
        }
    }
}
