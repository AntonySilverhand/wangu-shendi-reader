package org.wanshu.reader.core.source;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SyntheticPageFetcher implements PageFetcher {
    private final Map<Integer, RawChapterPage> map = new HashMap<Integer, RawChapterPage>();
    private final Set<Integer> failPages = new HashSet<Integer>();

    public SyntheticPageFetcher(List<RawChapterPage> pages) {
        for (int i = 0; i < pages.size(); i++) {
            RawChapterPage p = pages.get(i);
            map.put(p.getPageIndex(), p);
        }
    }

    public void addFailure(int pageIndex) {
        failPages.add(pageIndex);
    }

    @Override
    public PageFetchOutcome fetch(String chapterId, int pageIndex) {
        if (failPages.contains(pageIndex)) {
            return PageFetchOutcome.failed(new RuntimeException("Page " + pageIndex + " failed to fetch"));
        }
        RawChapterPage p = map.get(pageIndex);
        if (p == null) {
            return PageFetchOutcome.notFound();
        }
        return PageFetchOutcome.ok(p);
    }
}
