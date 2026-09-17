package org.wanshu.reader.download;

import java.util.List;
import org.wanshu.reader.core.download.DownloadDemandFlags;
import org.wanshu.reader.core.download.DownloadPriority;
import org.wanshu.reader.core.download.DownloadTaskState;
import org.wanshu.reader.core.net.SourceHttpClient;
import org.wanshu.reader.core.queue.RequestKey;
import org.wanshu.reader.core.queue.RequestPriority;
import org.wanshu.reader.core.queue.RequestScheduler;
import org.wanshu.reader.core.util.Base64Decoder;
import org.wanshu.reader.data.content.ContentDatabase;
import org.wanshu.reader.data.content.entity.ChapterEntity;
import org.wanshu.reader.data.content.entity.DownloadPolicyEntity;
import org.wanshu.reader.data.content.entity.DownloadTaskEntity;
import org.wanshu.reader.data.content.entity.SourceCooldownEntity;
import org.wanshu.reader.data.content.entity.TocEntryEntity;
import org.wanshu.reader.data.repository.FetchChapterSchedulerAction;

public class DownloadPumpRunnable implements Runnable {
    private final DownloadCoordinator coordinator;
    private final ContentDatabase db;
    private final RequestScheduler scheduler;
    private final SourceHttpClient httpClient;
    private final Base64Decoder decoder;

    public DownloadPumpRunnable(
            DownloadCoordinator coordinator,
            ContentDatabase db,
            RequestScheduler scheduler,
            SourceHttpClient httpClient,
            Base64Decoder decoder
    ) {
        this.coordinator = coordinator;
        this.db = db;
        this.scheduler = scheduler;
        this.httpClient = httpClient;
        this.decoder = decoder;
    }

    @Override
    public void run() {
        if (coordinator == null || db == null || scheduler == null) return;

        try {
            if (!coordinator.isReadyToRun()) {
                return;
            }

            String bookId = coordinator.getActiveBookId();
            if (bookId == null || bookId.isEmpty()) {
                return;
            }

            // 1. Check source cooldown
            SourceCooldownEntity cooldown = db.sourceCooldownDao().getCooldown("wanshuge");
            if (cooldown != null && cooldown.cooldownUntil > System.currentTimeMillis()) {
                coordinator.notifyProgressWithReason(PauseReason.RATE_LIMITED);
                return;
            }

            // 2. Query highest priority ready task
            List<DownloadTaskEntity> ready = db.downloadTaskDao().getReadyTasks(bookId, System.currentTimeMillis(), 1);
            if (ready == null || ready.isEmpty()) {
                coordinator.setCurrentChapter("", "");
                coordinator.notifyProgress();
                return;
            }

            DownloadTaskEntity task = ready.get(0);

            // 3. For speculative auto/prefetch tasks, check ReadingSessionGate
            if ((task.demandFlags & DownloadDemandFlags.DEMAND_MANUAL) == 0) {
                ReadingSessionGate gate = coordinator.getReadingSessionGate();
                if (gate != null) {
                    DownloadPolicyEntity policy = db.downloadPolicyDao().getPolicy(bookId);
                    long currentBytes = db.chapterDao().getTotalBytes(bookId);
                    GateResult gateResult = gate.evaluate(bookId, policy, currentBytes);
                    if (!gateResult.allowed) {
                        coordinator.notifyProgressWithReason(gateResult.reason);
                        return;
                    }
                }
            }

            // 4. Zero network skip on already complete chapter
            ChapterEntity existing = db.chapterDao().getChapter(bookId, task.chapterId);
            if (existing != null && existing.isComplete) {
                db.downloadTaskDao().updateTaskState(
                        bookId,
                        task.chapterId,
                        DownloadTaskState.COMPLETE,
                        task.attempts,
                        0L,
                        "",
                        0L
                );
                coordinator.pump();
                return;
            }

            long token = coordinator.incrementRunToken();
            db.downloadTaskDao().updateTaskState(
                    bookId,
                    task.chapterId,
                    DownloadTaskState.RUNNING,
                    task.attempts,
                    0L,
                    "",
                    token
            );

            TocEntryEntity toe = db.tocDao().getEntry(bookId, task.chapterId);
            String title = toe != null ? toe.title : ("第 " + task.chapterId + " 章");
            coordinator.setCurrentChapter(task.chapterId, title);
            coordinator.notifyProgress();

            RequestKey key = RequestKey.chapterAll(bookId, task.chapterId);
            FetchChapterSchedulerAction action = new FetchChapterSchedulerAction(bookId, task.chapterId, httpClient, decoder);
            DownloadSchedulerCallback cb = new DownloadSchedulerCallback(coordinator, task, token);

            RequestPriority prio = (task.priority >= DownloadPriority.PREFETCH) ? RequestPriority.NORMAL : RequestPriority.LOW;
            scheduler.enqueue(key, prio, "download-coordinator", action, cb);
        } finally {
            coordinator.setPumpRunning(false);
        }
    }
}
