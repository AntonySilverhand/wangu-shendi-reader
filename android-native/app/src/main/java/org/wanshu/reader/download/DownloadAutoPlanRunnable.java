package org.wanshu.reader.download;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.wanshu.reader.core.download.DownloadDemandFlags;
import org.wanshu.reader.core.download.DownloadPlanner;
import org.wanshu.reader.core.download.DownloadPriority;
import org.wanshu.reader.core.download.DownloadRange;
import org.wanshu.reader.core.download.DownloadTaskState;
import org.wanshu.reader.data.content.ContentDatabase;
import org.wanshu.reader.data.content.entity.DownloadPlanEntity;
import org.wanshu.reader.data.content.entity.DownloadPolicyEntity;
import org.wanshu.reader.data.content.entity.DownloadTaskEntity;
import org.wanshu.reader.data.content.entity.TocEntryEntity;

public class DownloadAutoPlanRunnable implements Runnable {

    private final DownloadCoordinator coordinator;
    private final ContentDatabase contentDb;
    private final String bookId;
    private final String currentChapterId;

    public DownloadAutoPlanRunnable(
            DownloadCoordinator coordinator,
            ContentDatabase contentDb,
            String bookId,
            String currentChapterId
    ) {
        this.coordinator = coordinator;
        this.contentDb = contentDb;
        this.bookId = bookId;
        this.currentChapterId = currentChapterId;
    }

    @Override
    public void run() {
        if (bookId == null || !bookId.equals("36780")) {
            return;
        }

        DownloadPolicyEntity policy = contentDb.downloadPolicyDao().getPolicy(bookId);
        if (policy == null) {
            policy = new DownloadPolicyEntity();
            policy.bookId = bookId;
            policy.autoCacheEnabled = false;
            policy.scope = "ENTIRE_BOOK";
            policy.allowMetered = false;
            policy.maxCacheBytes = 256L * 1024L * 1024L;
            contentDb.downloadPolicyDao().savePolicy(policy);
        }

        if (!policy.autoCacheEnabled) {
            return;
        }

        DownloadPlanEntity plan = contentDb.downloadPlanDao().getPlan(bookId);
        if (plan == null) {
            plan = new DownloadPlanEntity();
            plan.planId = "plan-" + bookId;
            plan.bookId = bookId;
            plan.scope = policy.scope != null ? policy.scope : "ENTIRE_BOOK";
            plan.initialAnchorChapterId = (currentChapterId != null && !currentChapterId.isEmpty()) ? currentChapterId : "1";
            plan.currentPhase = "PHASE_AFTER_ANCHOR_NEAR";
            plan.scanCheckpointChapterId = "";
            plan.createdAt = System.currentTimeMillis();
            plan.updatedAt = System.currentTimeMillis();
            contentDb.downloadPlanDao().savePlan(plan);
        }

        List<TocEntryEntity> entries = contentDb.tocDao().getEntries(bookId);
        if (entries == null || entries.isEmpty()) {
            return;
        }

        List<String> tocChapterIds = new ArrayList<String>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            tocChapterIds.add(entries.get(i).chapterId);
        }

        DownloadRange scopeRange = DownloadRange.ENTIRE_BOOK;
        if ("NEXT_50".equals(plan.scope)) {
            scopeRange = DownloadRange.NEXT_50;
        } else if ("NEXT_100".equals(plan.scope)) {
            scopeRange = DownloadRange.NEXT_100;
        } else if ("NEXT_200".equals(plan.scope)) {
            scopeRange = DownloadRange.NEXT_200;
        } else if ("NEXT_300".equals(plan.scope)) {
            scopeRange = DownloadRange.NEXT_300;
        }

        List<String> planned = DownloadPlanner.buildPlannedChapterOrder(
                tocChapterIds,
                plan.initialAnchorChapterId,
                scopeRange
        );

        List<String> completedList = contentDb.chapterDao().getCompletedChapterIds(bookId);
        Set<String> completedIds = new HashSet<String>(completedList != null ? completedList : new ArrayList<String>());

        List<DownloadTaskEntity> newTasks = new ArrayList<DownloadTaskEntity>();
        for (int i = 0; i < planned.size(); i++) {
            String chId = planned.get(i);
            DownloadTaskEntity existing = contentDb.downloadTaskDao().getTask(bookId, chId);
            if (existing == null) {
                DownloadTaskEntity task = new DownloadTaskEntity();
                task.bookId = bookId;
                task.chapterId = chId;
                task.demandFlags = DownloadDemandFlags.DEMAND_AUTO;
                task.state = completedIds.contains(chId) ? DownloadTaskState.COMPLETE : DownloadTaskState.PENDING;
                task.priority = DownloadPriority.AUTO_CACHE;
                task.attempts = 0;
                task.nextAttemptAt = 0L;
                task.errorType = "";
                task.runToken = 0L;
                newTasks.add(task);
            } else {
                if (completedIds.contains(chId) && !DownloadTaskState.COMPLETE.equals(existing.state)) {
                    existing.state = DownloadTaskState.COMPLETE;
                    contentDb.downloadTaskDao().insertTask(existing);
                }
            }
        }

        if (!newTasks.isEmpty()) {
            contentDb.downloadTaskDao().insertTasks(newTasks);
        }

        // Dynamic elevation for missing chapters near current reading position
        if (currentChapterId != null && !currentChapterId.isEmpty()) {
            List<String> elevated = DownloadPlanner.getElevatedChapters(
                    tocChapterIds,
                    currentChapterId,
                    3,
                    completedIds
            );
            for (int i = 0; i < elevated.size(); i++) {
                String elId = elevated.get(i);
                contentDb.downloadTaskDao().elevatePriority(
                        bookId,
                        elId,
                        DownloadPriority.PREFETCH,
                        DownloadDemandFlags.DEMAND_PREFETCH
                );
            }
        }

        plan.updatedAt = System.currentTimeMillis();
        contentDb.downloadPlanDao().savePlan(plan);

        coordinator.onTasksEnqueued();
    }
}
