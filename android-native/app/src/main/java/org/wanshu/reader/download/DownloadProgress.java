package org.wanshu.reader.download;

import java.util.Locale;

public class DownloadProgress {
    private final String bookId;
    private final int totalChapters;
    private final int completedChapters;
    private final int partialChapters;
    private final int pendingChapters;
    private final int failedChapters;
    private final String currentChapterId;
    private final String currentChapterTitle;
    private final boolean isRunning;
    private final boolean isPaused;
    private final String pauseReason;
    private final int sessionNewCompleted;
    private final int consecutiveOfflineCount;
    private final long cacheBytes;
    private final boolean autoCacheEnabled;
    private final String summaryText;

    public DownloadProgress(
            String bookId,
            int totalChapters,
            int completedChapters,
            int partialChapters,
            int pendingChapters,
            int failedChapters,
            String currentChapterId,
            String currentChapterTitle,
            boolean isRunning,
            boolean isPaused,
            String pauseReason,
            int sessionNewCompleted
    ) {
        this(bookId, totalChapters, completedChapters, partialChapters, pendingChapters, failedChapters,
                currentChapterId, currentChapterTitle, isRunning, isPaused, pauseReason, sessionNewCompleted,
                0, 0L, false, "");
    }

    public DownloadProgress(
            String bookId,
            int totalChapters,
            int completedChapters,
            int partialChapters,
            int pendingChapters,
            int failedChapters,
            String currentChapterId,
            String currentChapterTitle,
            boolean isRunning,
            boolean isPaused,
            String pauseReason,
            int sessionNewCompleted,
            int consecutiveOfflineCount,
            long cacheBytes,
            boolean autoCacheEnabled,
            String summaryText
    ) {
        this.bookId = bookId != null ? bookId : "";
        this.totalChapters = totalChapters;
        this.completedChapters = completedChapters;
        this.partialChapters = partialChapters;
        this.pendingChapters = pendingChapters;
        this.failedChapters = failedChapters;
        this.currentChapterId = currentChapterId != null ? currentChapterId : "";
        this.currentChapterTitle = currentChapterTitle != null ? currentChapterTitle : "";
        this.isRunning = isRunning;
        this.isPaused = isPaused;
        this.pauseReason = pauseReason != null ? pauseReason : "";
        this.sessionNewCompleted = sessionNewCompleted;
        this.consecutiveOfflineCount = consecutiveOfflineCount;
        this.cacheBytes = cacheBytes;
        this.autoCacheEnabled = autoCacheEnabled;
        this.summaryText = summaryText != null ? summaryText : "";
    }

    public String getBookId() {
        return bookId;
    }

    public int getTotalChapters() {
        return totalChapters;
    }

    public int getCompletedChapters() {
        return completedChapters;
    }

    public int getPartialChapters() {
        return partialChapters;
    }

    public int getPendingChapters() {
        return pendingChapters;
    }

    public int getFailedChapters() {
        return failedChapters;
    }

    public String getCurrentChapterId() {
        return currentChapterId;
    }

    public String getCurrentChapterTitle() {
        return currentChapterTitle;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public boolean isPaused() {
        return isPaused;
    }

    public String getPauseReason() {
        return pauseReason;
    }

    public int getSessionNewCompleted() {
        return sessionNewCompleted;
    }

    public int getConsecutiveOfflineCount() {
        return consecutiveOfflineCount;
    }

    public long getCacheBytes() {
        return cacheBytes;
    }

    public boolean isAutoCacheEnabled() {
        return autoCacheEnabled;
    }

    public String getSummaryText() {
        return summaryText;
    }

    public float getPercent() {
        if (totalChapters <= 0) return 0.0f;
        return Math.min(100.0f, (completedChapters * 100.0f) / totalChapters);
    }
}
