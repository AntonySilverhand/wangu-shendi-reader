package org.wanshu.reader.core.search;

import java.util.Collections;
import java.util.List;

/** Total match count plus one bounded window of original-text positions. */
public final class ChapterSearchPage {
    private final int startIndex, totalMatches;
    private final List<ChapterSearchMatch> matches;
    ChapterSearchPage(int startIndex, int totalMatches, List<ChapterSearchMatch> matches) {
        this.startIndex = startIndex; this.totalMatches = totalMatches;
        this.matches = Collections.unmodifiableList(matches);
    }
    public int getStartIndex() { return startIndex; }
    public int getTotalMatches() { return totalMatches; }
    public List<ChapterSearchMatch> getMatches() { return matches; }
}
