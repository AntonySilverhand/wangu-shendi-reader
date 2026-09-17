package org.wanshu.reader.core.search;

import java.util.ArrayList;
import java.util.List;

public class ChapterSearchEngine {
    public static final int MAX_HIGHLIGHTS = 500;

    public static List<ChapterSearchMatch> search(List<String> paragraphs, String query) {
        List<ChapterSearchMatch> matches = new ArrayList<ChapterSearchMatch>();
        if (paragraphs == null || paragraphs.isEmpty() || query == null) {
            return matches;
        }

        String q = query.trim().toLowerCase();
        if (q.isEmpty()) {
            return matches;
        }

        int qLen = q.length();

        for (int pIdx = 0; pIdx < paragraphs.size() && matches.size() < MAX_HIGHLIGHTS; pIdx++) {
            String p = paragraphs.get(pIdx);
            if (p == null || p.isEmpty()) {
                continue;
            }

            String lowerP = p.toLowerCase();
            int start = 0;
            while (start < lowerP.length() && matches.size() < MAX_HIGHLIGHTS) {
                int found = lowerP.indexOf(q, start);
                if (found == -1) {
                    break;
                }
                matches.add(new ChapterSearchMatch(pIdx, found, found + qLen));
                start = found + qLen;
            }
        }

        return matches;
    }
}
