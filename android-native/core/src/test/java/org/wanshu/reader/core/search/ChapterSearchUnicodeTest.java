package org.wanshu.reader.core.search;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import static org.junit.Assert.*;

public class ChapterSearchUnicodeTest {
    @Test public void offsetsAddressOriginalUtf16EvenAfterExpandingCaseCharacters() {
        String text = "İ😀ABC，abc";
        List<ChapterSearchMatch> found = ChapterSearchEngine.search(Collections.singletonList(text), "abc");
        assertEquals(2, found.size());
        assertEquals(3, found.get(0).getStartOffset());
        assertEquals("ABC", text.substring(found.get(0).getStartOffset(), found.get(0).getEndOffset()));
        assertEquals(7, found.get(1).getStartOffset());
    }
    @Test public void matchingDoesNotDependOnDeviceLocale() {
        Locale old = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            assertEquals(2, ChapterSearchEngine.search(Collections.singletonList("I i"), "i").size());
        } finally { Locale.setDefault(old); }
    }
    @Test public void resultsAreBoundedAndParagraphOffsetsStable() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 1000; i++) text.append("万古");
        List<ChapterSearchMatch> found = ChapterSearchEngine.search(Arrays.asList("无匹配", text.toString()), "万古");
        assertEquals(500, found.size());
        assertEquals(1, found.get(499).getParagraphIndex());
        assertEquals(998, found.get(499).getStartOffset());
        assertTrue(ChapterSearchEngine.search(Collections.singletonList("正文"), "  ").isEmpty());
    }
}
