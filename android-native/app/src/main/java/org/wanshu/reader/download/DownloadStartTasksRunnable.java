package org.wanshu.reader.download;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.wanshu.reader.core.download.DownloadDemandFlags;
import org.wanshu.reader.core.download.DownloadRange;
import org.wanshu.reader.core.download.DownloadTaskState;
import org.wanshu.reader.core.net.SourceHttpClient;
import org.wanshu.reader.data.content.ContentDatabase;
import org.wanshu.reader.data.content.entity.DownloadTaskEntity;
import org.wanshu.reader.data.content.entity.TocEntryEntity;
import org.wanshu.reader.ui.toc.ContentTocSearchDeps;

public class DownloadStartTasksRunnable implements Runnable {
    private final DownloadCoordinator coordinator;
    private final ContentDatabase db;
    private final String bookId;
    private final String startChapterId;
    private final DownloadRange range;
    private final SourceHttpClient httpClient;

    public DownloadStartTasksRunnable(
            DownloadCoordinator coordinator,
            ContentDatabase db,
            String bookId,
            String startChapterId,
            DownloadRange range,
            SourceHttpClient httpClient
    ) {
        this.coordinator = coordinator;
        this.db = db;
        this.bookId = bookId != null ? bookId : "";
        this.startChapterId = startChapterId != null ? startChapterId : "";
        this.range = range != null ? range : DownloadRange.ENTIRE_BOOK;
        this.httpClient = httpClient;
    }

    @Override
    public void run() {
        if (db == null || bookId.isEmpty()) return;

        boolean isLocal = bookId.startsWith("local-");

        // 1. If online book and TOC is incomplete, ensure necessary TOC is loaded
        if (!isLocal && httpClient != null) {
            int loadedCount = db.tocDao().getLoadedPagesCount(bookId);
            ContentTocSearchDeps tocDeps = new ContentTocSearchDeps(bookId, db, httpClient);
            try {
                if (range == DownloadRange.ENTIRE_BOOK && loadedCount < ContentTocSearchDeps.ONLINE_TOTAL_PAGES) {
                    tocDeps.loadRange(1, ContentTocSearchDeps.ONLINE_TOTAL_PAGES, null);
                } else if (loadedCount == 0) {
                    tocDeps.loadRange(1, 4, null);
                }
            } catch (Exception ignored) {
            }
        }

        // 2. Fetch all entries
        List<TocEntryEntity> allEntries = db.tocDao().getEntries(bookId);
        if (allEntries == null || allEntries.isEmpty()) {
            if (coordinator != null) {
                coordinator.notifyProgress();
            }
            return;
        }

        // 3. Determine slice
        List<TocEntryEntity> targetSlice;
        if (range == DownloadRange.ENTIRE_BOOK) {
            targetSlice = allEntries;
        } else {
            int startIdx = 0;
            if (!startChapterId.isEmpty()) {
                for (int i = 0; i < allEntries.size(); i++) {
                    if (startChapterId.equals(allEntries.get(i).chapterId)) {
                        startIdx = i;
                        break;
                    }
                }
            }
            int endIdx = Math.min(allEntries.size(), startIdx + range.getCount());
            targetSlice = allEntries.subList(startIdx, endIdx);
        }

        // 4. Query already completed chapters in DB (Zero-network skip)
        List<String> completedIds = db.chapterDao().getCompletedChapterIds(bookId);
        Set<String> completedSet = new HashSet<String>(completedIds != null ? completedIds : new ArrayList<String>());

        // 5. Build tasks
        List<DownloadTaskEntity> tasks = new ArrayList<DownloadTaskEntity>();
        for (int i = 0; i < targetSlice.size(); i++) {
            TocEntryEntity entry = targetSlice.get(i);
            if (completedSet.contains(entry.chapterId)) {
                continue;
            }
            DownloadTaskEntity t = new DownloadTaskEntity();
            t.bookId = bookId;
            t.chapterId = entry.chapterId;
            t.demandFlags = DownloadDemandFlags.DEMAND_MANUAL;
            t.state = DownloadTaskState.PENDING;
            t.priority = 50;
            t.attempts = 0;
            t.nextAttemptAt = 0L;
            t.errorType = "";
            t.runToken = 0L;
            tasks.add(t);
        }

        if (!tasks.isEmpty()) {
            db.downloadTaskDao().insertTasks(tasks);
        }

        if (coordinator != null) {
            coordinator.onTasksEnqueued();
        }
    }
}
