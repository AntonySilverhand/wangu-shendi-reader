package org.wanshu.reader.core.search;

import java.util.ArrayList;
import java.util.List;
import org.wanshu.reader.core.net.CancellationToken;

public class ChapterSearchEngine {
    public static final int MAX_HIGHLIGHTS = 500;

    public static List<ChapterSearchMatch> search(List<String> paragraphs, String query) {
        return searchPage(paragraphs, query, 0).getMatches();
    }

    public static ChapterSearchPage searchPage(List<String> paragraphs, String query, int startIndex) {
        return searchPage(paragraphs, query, startIndex, null);
    }

    public static ChapterSearchPage searchPage(List<String> paragraphs, String query, int startIndex, CancellationToken token) {
        int first = Math.max(0, startIndex), total = 0;
        List<ChapterSearchMatch> matches = new ArrayList<ChapterSearchMatch>();
        String q = query == null ? "" : query.trim();
        if (paragraphs == null || q.isEmpty()) return new ChapterSearchPage(first, 0, matches);
        int qLen = q.length();
        for (int pIdx = 0; pIdx < paragraphs.size(); pIdx++) {
            if (token != null && token.isCancelled()) {
                matches.clear(); return new ChapterSearchPage(first, 0, matches);
            }
            String p = paragraphs.get(pIdx);
            if (p == null) continue;
            // Compare original text: lowercasing can expand UTF-16 and corrupt anchors.
            int start = 0;
            while (start <= p.length() - qLen) {
                if (token != null && token.isCancelled()) {
                    matches.clear(); return new ChapterSearchPage(first, 0, matches);
                }
                if (p.regionMatches(true, start, q, 0, qLen)) {
                    if (total >= first && matches.size() < MAX_HIGHLIGHTS)
                        matches.add(new ChapterSearchMatch(pIdx, start, start + qLen));
                    total++;
                    start += qLen;
                } else start++;
            }
        }
        return new ChapterSearchPage(first, total, matches);
    }
}
