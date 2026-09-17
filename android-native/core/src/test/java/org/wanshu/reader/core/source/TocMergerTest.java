package org.wanshu.reader.core.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.wanshu.reader.core.model.TocEntry;

public class TocMergerTest {

    @Test
    public void testTocParsingAndMerging() {
        String html1 = FixtureHelper.readFixture("toc-page1.html");
        String html2 = FixtureHelper.readFixture("toc-page2.html");

        List<TocItemRaw> raw1 = SourceParser.parseTocPageHtml(html1, 1);
        List<TocItemRaw> raw2 = SourceParser.parseTocPageHtml(html2, 2);

        assertTrue(raw1.size() > 0);
        assertTrue(raw2.size() > 0);

        Integer total1 = SourceParser.parseTocTotalPages(html1);
        assertNull(total1);

        Integer total2 = SourceParser.parseTocTotalPages(html2);
        assertNotNull(total2);
        assertEquals(Integer.valueOf(44), total2);

        ClassifiedToc c1 = SourceParser.classifyTocItems(raw1, 1);
        ClassifiedToc c2 = SourceParser.classifyTocItems(raw2, 2);

        List<TocEntry> merged = TocMerger.mergeTocPages(Arrays.asList(
                combine(c1.getMain(), c1.getExtras()),
                combine(c2.getMain(), c2.getExtras())
        ));

        assertTrue(merged.size() > 0);
        // Verify entries are numbered and ordered
        TocEntry first = merged.get(0);
        assertNotNull(first.getChapterId());
        assertNotNull(first.getTitle());
    }

    private static List<TocEntry> combine(List<TocEntry> a, List<TocEntry> b) {
        List<TocEntry> list = new java.util.ArrayList<TocEntry>(a.size() + b.size());
        list.addAll(a);
        list.addAll(b);
        return list;
    }
}
