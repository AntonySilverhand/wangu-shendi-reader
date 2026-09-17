package org.wanshu.reader.download;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.wanshu.reader.core.download.DownloadRange;
import org.wanshu.reader.core.model.ChapterResult;
import org.wanshu.reader.core.net.SourceHttpClient;
import org.wanshu.reader.core.queue.RequestScheduler;
import org.wanshu.reader.core.text.TextBlockBuilder;
import org.wanshu.reader.core.util.Base64Decoder;
import org.wanshu.reader.data.AppExecutors;
import org.wanshu.reader.data.content.ContentDatabase;
import org.wanshu.reader.data.content.entity.DownloadTaskEntity;
import org.wanshu.reader.data.repository.SaveChapterRunnable;

public class DownloadCoordinator {
    private final ContentDatabase contentDb;
    private final RequestScheduler scheduler;
    private final SourceHttpClient httpClient;
    private final AppExecutors executors;
    private final TextBlockBuilder textBlockBuilder;
    private final Base64Decoder decoder;
    private final ReadingSessionGate readingSessionGate;

    private final List<DownloadProgressListener> listeners = new CopyOnWriteArrayList<DownloadProgressListener>();

    private volatile boolean isAppForeground = true;
    private volatile boolean isPaused = false;
    private volatile boolean isPumping = false;
    private volatile String activeBookId = "";
    private volatile String currentChapterId = "";
    private volatile String currentChapterTitle = "";

    private final AtomicLong currentRunToken = new AtomicLong(1L);
    private final AtomicInteger sessionNewCompleted = new AtomicInteger(0);

    public DownloadCoordinator(
            ContentDatabase contentDb,
            RequestScheduler scheduler,
            SourceHttpClient httpClient,
            AppExecutors executors,
            TextBlockBuilder textBlockBuilder,
            Base64Decoder decoder,
            ReadingSessionGate readingSessionGate
    ) {
        this.contentDb = contentDb;
        this.scheduler = scheduler;
        this.httpClient = httpClient;
        this.executors = executors;
        this.textBlockBuilder = textBlockBuilder;
        this.decoder = decoder;
        this.readingSessionGate = readingSessionGate != null ? readingSessionGate : new ReadingSessionGate(null);
    }

    public DownloadCoordinator(
            ContentDatabase contentDb,
            RequestScheduler scheduler,
            SourceHttpClient httpClient,
            AppExecutors executors,
            TextBlockBuilder textBlockBuilder,
            Base64Decoder decoder
    ) {
        this(contentDb, scheduler, httpClient, executors, textBlockBuilder, decoder, null);
    }

    public ReadingSessionGate getReadingSessionGate() {
        return readingSessionGate;
    }

    public void addListener(DownloadProgressListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(DownloadProgressListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    public void setAppForeground(boolean foreground) {
        if (this.isAppForeground == foreground) {
            return;
        }
        this.isAppForeground = foreground;
        readingSessionGate.setActivityResumed(foreground);
        if (!foreground) {
            if (scheduler != null) {
                scheduler.cancelAllForSubscriber("download-coordinator");
                scheduler.cancelAllForSubscriber("download-coordinator-auto");
            }
            notifyProgress();
        } else {
            executors.getContentDbExecutor().execute(new DownloadResetOrphanedRunnable(contentDb, this));
            if (readingSessionGate.isReaderActive() && "36780".equals(activeBookId)) {
                onReadingChapterChanged(activeBookId, currentChapterId, currentChapterTitle);
            }
        }
    }

    public boolean isAppForeground() {
        return isAppForeground;
    }

    public boolean isReadyToRun() {
        return isAppForeground && !isPaused && activeBookId != null && !activeBookId.isEmpty();
    }

    public void setPumpRunning(boolean running) {
        this.isPumping = running;
    }

    public long incrementRunToken() {
        return currentRunToken.incrementAndGet();
    }

    public long getCurrentRunToken() {
        return currentRunToken.get();
    }

    public String getActiveBookId() {
        return activeBookId;
    }

    public void setCurrentChapter(String id, String title) {
        this.currentChapterId = id != null ? id : "";
        this.currentChapterTitle = title != null ? title : "";
    }

    public void onReadingChapterChanged(String bookId, String chapterId, String chapterTitle) {
        if (bookId == null) return;
        this.activeBookId = bookId;
        this.currentChapterId = chapterId != null ? chapterId : "";
        this.currentChapterTitle = chapterTitle != null ? chapterTitle : "";
        readingSessionGate.setReaderActive(true, bookId);

        if ("36780".equals(bookId)) {
            executors.getContentDbExecutor().execute(new DownloadAutoPlanRunnable(
                    this,
                    contentDb,
                    bookId,
                    chapterId
            ));
        }
        notifyProgress();
    }

    public void onReaderClosed() {
        readingSessionGate.setReaderActive(false, "");
        if (scheduler != null) {
            scheduler.cancelAllForSubscriber("download-coordinator-auto");
        }
        notifyProgress();
    }

    public void onPolicyChanged(String bookId) {
        if (bookId != null && bookId.equals(activeBookId) && "36780".equals(bookId)) {
            executors.getContentDbExecutor().execute(new DownloadAutoPlanRunnable(
                    this,
                    contentDb,
                    bookId,
                    currentChapterId
            ));
        }
        notifyProgress();
        pump();
    }

    public void incrementSessionNewCompleted() {
        sessionNewCompleted.incrementAndGet();
    }

    public void startManualDownload(String bookId, String startChapterId, DownloadRange range) {
        this.activeBookId = bookId != null ? bookId : "";
        this.isPaused = false;
        this.sessionNewCompleted.set(0);
        executors.getContentDbExecutor().execute(new DownloadStartTasksRunnable(
                this,
                contentDb,
                bookId,
                startChapterId,
                range,
                httpClient
        ));
    }

    public void onTasksEnqueued() {
        notifyProgress();
        pump();
    }

    public void pauseDownload(String bookId) {
        this.isPaused = true;
        if (scheduler != null) {
            scheduler.cancelAllForSubscriber("download-coordinator");
            scheduler.cancelAllForSubscriber("download-coordinator-auto");
        }
        notifyProgress();
    }

    public void resumeDownload(String bookId) {
        if (bookId != null && !bookId.isEmpty()) {
            this.activeBookId = bookId;
        }
        this.isPaused = false;
        pump();
    }

    public void cancelDownload(String bookId) {
        this.isPaused = true;
        if (scheduler != null) {
            scheduler.cancelAllForSubscriber("download-coordinator");
            scheduler.cancelAllForSubscriber("download-coordinator-auto");
        }
        executors.getContentDbExecutor().execute(new DownloadCancelTasksRunnable(contentDb, bookId, this));
    }

    public void retryFailed(String bookId) {
        if (bookId != null && !bookId.isEmpty()) {
            this.activeBookId = bookId;
        }
        this.isPaused = false;
        executors.getContentDbExecutor().execute(new DownloadRetryFailedRunnable(contentDb, bookId, this));
    }

    public void pump() {
        if (!isReadyToRun() || isPumping) {
            return;
        }
        this.isPumping = true;
        executors.getContentDbExecutor().execute(new DownloadPumpRunnable(
                this,
                contentDb,
                scheduler,
                httpClient,
                decoder
        ));
    }

    public void onChapterFetched(DownloadTaskEntity task, ChapterResult result, long token) {
        if (token != currentRunToken.get()) {
            return;
        }

        if (result != null && result.isComplete()) {
            executors.getContentDbExecutor().execute(new SaveChapterRunnable(
                    contentDb,
                    result,
                    "REMOTE",
                    textBlockBuilder,
                    new DownloadSaveCompletionCallback(this)
            ));
        } else {
            onChapterFetchFailed(task, new IllegalStateException("Fetched chapter incomplete or missing pages"), token);
        }
    }

    public void onChapterFetchFailed(DownloadTaskEntity task, Throwable error, long token) {
        if (token != currentRunToken.get()) {
            return;
        }
        executors.getContentDbExecutor().execute(new DownloadTaskErrorRunnable(this, contentDb, task, error));
    }

    public void onChapterFetchCancelled(DownloadTaskEntity task, long token) {
        executors.getContentDbExecutor().execute(new DownloadTaskCancelledRunnable(this, contentDb, task));
    }

    public void onChapterSaved() {
        incrementSessionNewCompleted();
        notifyProgress();
        pump();
    }

    public void notifyProgress() {
        notifyProgressWithReason("");
    }

    public void notifyProgressWithReason(String explicitReason) {
        String reason = explicitReason != null ? explicitReason : "";
        if (reason.isEmpty()) {
            if (!isAppForeground) {
                reason = PauseReason.APP_HIDDEN;
            } else if (isPaused) {
                reason = PauseReason.USER_PAUSED;
            }
        }

        executors.getContentDbExecutor().execute(new DownloadQueryProgressRunnable(
                contentDb,
                activeBookId,
                executors.getMainThreadExecutor(),
                listeners,
                isReadyToRun(),
                isPaused,
                reason,
                currentChapterId,
                currentChapterTitle,
                sessionNewCompleted.get()
        ));
    }
}
