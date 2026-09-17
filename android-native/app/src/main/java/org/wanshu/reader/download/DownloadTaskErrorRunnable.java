package org.wanshu.reader.download;

import org.wanshu.reader.core.download.DownloadTaskState;
import org.wanshu.reader.core.download.RetryPolicy;
import org.wanshu.reader.data.content.ContentDatabase;
import org.wanshu.reader.data.content.entity.DownloadTaskEntity;

public class DownloadTaskErrorRunnable implements Runnable {
    private final DownloadCoordinator coordinator;
    private final ContentDatabase db;
    private final DownloadTaskEntity task;
    private final Throwable error;

    public DownloadTaskErrorRunnable(
            DownloadCoordinator coordinator,
            ContentDatabase db,
            DownloadTaskEntity task,
            Throwable error
    ) {
        this.coordinator = coordinator;
        this.db = db;
        this.task = task;
        this.error = error;
    }

    @Override
    public void run() {
        if (task != null && db != null) {
            int newAttempts = task.attempts + 1;
            long delay = RetryPolicy.getBackoffDelayMillis(newAttempts);
            String newState;
            long nextAttemptAt = 0L;

            if (delay < 0L || newAttempts >= RetryPolicy.MAX_ATTEMPTS) {
                newState = DownloadTaskState.NEEDS_ACTION;
            } else {
                newState = DownloadTaskState.RETRY_AT;
                nextAttemptAt = System.currentTimeMillis() + delay;
            }

            String errorMsg = error != null ? (error.getClass().getSimpleName() + ": " + error.getMessage()) : "Unknown error";
            db.downloadTaskDao().updateTaskState(
                    task.bookId,
                    task.chapterId,
                    newState,
                    newAttempts,
                    nextAttemptAt,
                    errorMsg,
                    0L
            );

            // Record cooldown if HTTP 429/503 rate-limited
            if (errorMsg.contains("429") || errorMsg.contains("503") || errorMsg.contains("cooldown") || errorMsg.contains("Rate limit")) {
                org.wanshu.reader.data.content.entity.SourceCooldownEntity cooldown = new org.wanshu.reader.data.content.entity.SourceCooldownEntity();
                cooldown.sourceId = "wanshuge";
                cooldown.cooldownUntil = System.currentTimeMillis() + 60000L;
                cooldown.reason = errorMsg;
                db.sourceCooldownDao().setCooldown(cooldown);
            }
        }

        if (coordinator != null) {
            coordinator.notifyProgress();
            coordinator.pump();
        }
    }
}
