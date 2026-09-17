package org.wanshu.reader.core.download;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class DownloadPlanner {

    public static List<String> buildPlannedChapterOrder(
            List<String> tocChapterIds,
            String initialAnchorChapterId,
            DownloadRange scope
    ) {
        if (tocChapterIds == null || tocChapterIds.isEmpty()) {
            return Collections.emptyList();
        }

        int totalChapters = tocChapterIds.size();
        int anchorIndex = -1;
        if (initialAnchorChapterId != null && !initialAnchorChapterId.isEmpty()) {
            anchorIndex = tocChapterIds.indexOf(initialAnchorChapterId);
        }
        if (anchorIndex < 0) {
            anchorIndex = 0;
        }

        List<String> order = new ArrayList<String>(totalChapters);

        if (scope == DownloadRange.ENTIRE_BOOK) {
            // Phase 1: Near subsequent 50 chapters from anchor
            int nearEnd = Math.min(anchorIndex + 50, totalChapters - 1);
            for (int i = anchorIndex + 1; i <= nearEnd; i++) {
                order.add(tocChapterIds.get(i));
            }

            // Phase 2: Far subsequent chapters to end of TOC
            for (int i = anchorIndex + 51; i < totalChapters; i++) {
                order.add(tocChapterIds.get(i));
            }

            // Phase 3: Previous chapters from anchor backwards to book start
            for (int i = anchorIndex - 1; i >= 0; i--) {
                order.add(tocChapterIds.get(i));
            }

            // Also include anchor chapter itself if needed
            if (anchorIndex >= 0 && anchorIndex < totalChapters) {
                order.add(tocChapterIds.get(anchorIndex));
            }
        } else {
            // Limited subsequent window (NEXT_50, NEXT_100, NEXT_200, NEXT_300)
            int count = scope != null ? scope.getCount() : 50;
            int limit = Math.min(anchorIndex + count, totalChapters - 1);
            for (int i = anchorIndex + 1; i <= limit; i++) {
                order.add(tocChapterIds.get(i));
            }
        }

        return order;
    }

    public static List<String> getMissingChapters(
            List<String> plannedOrder,
            Set<String> completedChapterIds
    ) {
        if (plannedOrder == null || plannedOrder.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> missing = new ArrayList<String>();
        for (int i = 0; i < plannedOrder.size(); i++) {
            String chapterId = plannedOrder.get(i);
            if (completedChapterIds == null || !completedChapterIds.contains(chapterId)) {
                missing.add(chapterId);
            }
        }
        return missing;
    }

    public static List<String> getElevatedChapters(
            List<String> tocChapterIds,
            String currentReadingChapterId,
            int elevationWindowSize,
            Set<String> completedChapterIds
    ) {
        if (tocChapterIds == null || tocChapterIds.isEmpty() || currentReadingChapterId == null) {
            return Collections.emptyList();
        }
        int currentIndex = tocChapterIds.indexOf(currentReadingChapterId);
        if (currentIndex < 0) {
            return Collections.emptyList();
        }

        List<String> elevated = new ArrayList<String>();
        // Current chapter itself if not completed
        if (completedChapterIds == null || !completedChapterIds.contains(currentReadingChapterId)) {
            elevated.add(currentReadingChapterId);
        }

        // Subsequent near chapters within window
        int maxIndex = Math.min(currentIndex + elevationWindowSize, tocChapterIds.size() - 1);
        for (int i = currentIndex + 1; i <= maxIndex; i++) {
            String id = tocChapterIds.get(i);
            if (completedChapterIds == null || !completedChapterIds.contains(id)) {
                elevated.add(id);
            }
        }
        return elevated;
    }

    public static int calculateConsecutiveOfflineCount(
            List<String> tocChapterIds,
            String currentReadingChapterId,
            Set<String> completedChapterIds
    ) {
        if (tocChapterIds == null || tocChapterIds.isEmpty() || currentReadingChapterId == null || completedChapterIds == null) {
            return 0;
        }
        int currentIndex = tocChapterIds.indexOf(currentReadingChapterId);
        if (currentIndex < 0) {
            return 0;
        }

        int count = 0;
        for (int i = currentIndex + 1; i < tocChapterIds.size(); i++) {
            String id = tocChapterIds.get(i);
            if (completedChapterIds.contains(id)) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    public static List<String> getNextBatch(List<String> uncompletedChapters, int maxBatchSize) {
        if (uncompletedChapters == null || uncompletedChapters.isEmpty()) {
            return Collections.emptyList();
        }
        int batchSize = Math.min(maxBatchSize, uncompletedChapters.size());
        List<String> batch = new ArrayList<String>(batchSize);
        for (int i = 0; i < batchSize; i++) {
            batch.add(uncompletedChapters.get(i));
        }
        return batch;
    }
}
