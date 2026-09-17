package org.wanshu.reader.download;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executor;
import org.wanshu.reader.core.download.DownloadPlanner;
import org.wanshu.reader.data.content.ContentDatabase;
import org.wanshu.reader.data.content.entity.DownloadPolicyEntity;
import org.wanshu.reader.data.content.entity.TocEntryEntity;

public class DownloadQueryProgressRunnable implements Runnable {
    private final ContentDatabase contentDb;
    private final String bookId;
    private final Executor mainExecutor;
    private final List<DownloadProgressListener> listeners;
    private final boolean isRunning;
    private final boolean isPaused;
    private final String pauseReason;
    private final String currentChapterId;
    private final String currentChapterTitle;
    private final int sessionNewCompleted;

    public DownloadQueryProgressRunnable(
            ContentDatabase contentDb,
            String bookId,
            Executor mainExecutor,
            List<DownloadProgressListener> listeners,
            boolean isRunning,
            boolean isPaused,
            String pauseReason,
            String currentChapterId,
            String currentChapterTitle,
            int sessionNewCompleted
    ) {
        this.contentDb = contentDb;
        this.bookId = bookId != null ? bookId : "";
        this.mainExecutor = mainExecutor;
        this.listeners = listeners;
        this.isRunning = isRunning;
        this.isPaused = isPaused;
        this.pauseReason = pauseReason != null ? pauseReason : "";
        this.currentChapterId = currentChapterId != null ? currentChapterId : "";
        this.currentChapterTitle = currentChapterTitle != null ? currentChapterTitle : "";
        this.sessionNewCompleted = sessionNewCompleted;
    }

    @Override
    public void run() {
        int total = 0;
        int completed = 0;
        int partial = 0;
        int pending = 0;
        int failed = 0;
        int consecutiveOffline = 0;
        long cacheBytes = 0L;
        boolean autoCacheEnabled = false;

        try {
            total = contentDb.tocDao().getEntriesCount(bookId);
            completed = contentDb.chapterDao().getCompletedCount(bookId);
            partial = contentDb.chapterDao().getPartialCount(bookId);
            pending = contentDb.downloadTaskDao().getPendingCount(bookId);
            failed = contentDb.downloadTaskDao().getFailedCount(bookId);
            cacheBytes = contentDb.chapterDao().getTotalBytes(bookId);

            DownloadPolicyEntity policy = contentDb.downloadPolicyDao().getPolicy(bookId);
            autoCacheEnabled = policy != null && policy.autoCacheEnabled;

            if (currentChapterId != null && !currentChapterId.isEmpty()) {
                List<TocEntryEntity> entries = contentDb.tocDao().getEntries(bookId);
                if (entries != null && !entries.isEmpty()) {
                    List<String> ids = new ArrayList<String>(entries.size());
                    for (int i = 0; i < entries.size(); i++) {
                        ids.add(entries.get(i).chapterId);
                    }
                    List<String> completedList = contentDb.chapterDao().getCompletedChapterIds(bookId);
                    Set<String> completedSet = new HashSet<String>(completedList != null ? completedList : new ArrayList<String>());
                    consecutiveOffline = DownloadPlanner.calculateConsecutiveOfflineCount(ids, currentChapterId, completedSet);
                }
            }
        } catch (Exception ignored) {
        }

        float percent = total > 0 ? Math.min(100.0f, (completed * 100.0f) / (float) total) : 0.0f;
        String summary = "";
        if (isRunning) {
            summary = String.format(Locale.getDefault(), "下载中 · 整本 %,d / %,d章 (%.1f%%) · 后续连续可离线 %d章",
                    completed, total, percent, consecutiveOffline);
        } else if (isPaused || (!pauseReason.isEmpty() && !pauseReason.equals(PauseReason.NONE))) {
            summary = String.format(Locale.getDefault(), "已暂停 · 整本 %,d / %,d章 · %s",
                    completed, total, pauseReason);
        } else if (consecutiveOffline > 0) {
            summary = String.format(Locale.getDefault(), "后续连续可离线 %d章", consecutiveOffline);
        }

        DownloadProgress progress = new DownloadProgress(
                bookId,
                total,
                completed,
                partial,
                pending,
                failed,
                currentChapterId,
                currentChapterTitle,
                isRunning,
                isPaused,
                pauseReason,
                sessionNewCompleted,
                consecutiveOffline,
                cacheBytes,
                autoCacheEnabled,
                summary
        );

        if (mainExecutor != null && listeners != null) {
            mainExecutor.execute(new DownloadProgressUpdateRunnable(listeners, progress));
        }
    }
}
