package org.wanshu.reader.data.content;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChapterCacheStatus {
    private final Set<String> completedIds;
    private final Set<String> allCachedIds;

    public ChapterCacheStatus(List<String> completedList, List<String> allCachedList) {
        this.completedIds = completedList != null ? new HashSet<String>(completedList) : Collections.<String>emptySet();
        this.allCachedIds = allCachedList != null ? new HashSet<String>(allCachedList) : Collections.<String>emptySet();
    }

    public boolean isCompleted(String chapterId) {
        return completedIds.contains(chapterId);
    }

    public boolean isPartial(String chapterId) {
        return allCachedIds.contains(chapterId) && !completedIds.contains(chapterId);
    }

    public boolean isCached(String chapterId) {
        return allCachedIds.contains(chapterId);
    }
}
