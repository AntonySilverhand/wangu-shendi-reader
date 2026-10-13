package org.wanshu.reader.core.search;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import org.wanshu.reader.core.net.CancellationToken;
import static org.junit.Assert.*;

public class ChapterSearchPageTest {
    @Test public void everyMatchBeyondTenThousandIsReachableWithBoundedWindows() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 10003; i++) text.append("😀剑 ");
        int visited = 0;
        for (int start = 0; start < 10003; start += ChapterSearchEngine.MAX_HIGHLIGHTS) {
            ChapterSearchPage page = ChapterSearchEngine.searchPage(Collections.singletonList(text.toString()), "剑", start);
            assertEquals(10003, page.getTotalMatches());
            assertEquals(start, page.getStartIndex());
            assertTrue(page.getMatches().size() <= 500);
            for (ChapterSearchMatch match : page.getMatches()) {
                assertEquals(0, match.getParagraphIndex());
                assertEquals(visited * 4 + 2, match.getStartOffset());
                assertEquals("剑", text.substring(match.getStartOffset(), match.getEndOffset()));
                visited++;
            }
        }
        assertEquals(10003, visited);
    }

    @Test public void countAndWindowUseOriginalUtf16OffsetsAcrossParagraphs() {
        ChapterSearchPage page = ChapterSearchEngine.searchPage(Arrays.asList("İx", null, "😀X", "x"), "X", 1);
        assertEquals(3, page.getTotalMatches());
        assertEquals(2, page.getMatches().size());
        assertEquals(2, page.getMatches().get(0).getParagraphIndex());
        assertEquals(2, page.getMatches().get(0).getStartOffset());
        assertEquals(3, page.getMatches().get(1).getParagraphIndex());
    }

    @Test public void nonOverlappingMatchingIsUnchanged() {
        ChapterSearchPage page = ChapterSearchEngine.searchPage(Collections.singletonList("aaaaa"), "aa", 0);
        assertEquals(2, page.getTotalMatches());
        assertEquals(2, page.getMatches().get(1).getStartOffset());
    }

    @Test public void emptyAndOutOfRangeWindowsAreSafe() {
        assertEquals(0, ChapterSearchEngine.searchPage(null, "x", 0).getTotalMatches());
        assertEquals(0, ChapterSearchEngine.searchPage(Collections.singletonList("x"), "  ", 0).getTotalMatches());
        ChapterSearchPage outside = ChapterSearchEngine.searchPage(Collections.singletonList("xxx"), "x", Integer.MAX_VALUE);
        assertEquals(3, outside.getTotalMatches()); assertTrue(outside.getMatches().isEmpty());
        assertEquals(0, ChapterSearchEngine.searchPage(Collections.singletonList("x"), "x", -1).getStartIndex());
    }

    @Test public void cancelledSearchDoesNotScanOrPublishPartialMatches() {
        CancellationToken token = new CancellationToken(); token.cancel();
        ChapterSearchPage page = ChapterSearchEngine.searchPage(Collections.singletonList("xxx"), "x", 0, token);
        assertEquals(0, page.getTotalMatches()); assertTrue(page.getMatches().isEmpty());
    }
}
