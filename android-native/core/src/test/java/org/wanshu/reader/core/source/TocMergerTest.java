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

        List<TocItemRaw> raw1 = SourceParser.parseTocPageHtml("36780", html1, 1);
        List<TocItemRaw> raw2 = SourceParser.parseTocPageHtml("36780", html2, 2);

        assertTrue(raw1.size() > 0);
        assertTrue(raw2.size() > 0);

        Integer total1 = SourceParser.parseTocTotalPages("36780", html1);
        // The detail page (page 1) has no page selector: the total is unknown, never guessed.
        assertNull(total1);

        Integer total2 = SourceParser.parseTocTotalPages("36780", html2);
        assertNotNull(total2);
        assertEquals(Integer.valueOf(44), total2);

        ClassifiedToc c1 = SourceParser.classifyTocItems("36780", raw1, 1);
        ClassifiedToc c2 = SourceParser.classifyTocItems("36780", raw2, 2);

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

    @Test
    public void testMisnumberedSameBatchChaptersAreKept() {
        String[][] items = {
            {"38623350", "第3403章 甲"},
            {"38623354", "分节阅读第3404节"},
            {"38623375", "第3409章 天魔霸枪"},
            {"38623378", "第三千三百八十九章 又惹祸了"},
            {"38623395", "第三千四百零二章 进始祖界修为大进"},
            {"38623396", "分节阅读第3403节"},
            {"38623397", "第三千四百零四章 时间缓流区"},
            {"49080100", "分节阅读第3403节"},
            {"49080101", "第三千四百零四章 时间缓流区"},
        };
        List<TocEntry> merged = TocMerger.mergeTocPages(Arrays.asList(entries(items)));
        String[] ids = new String[merged.size()];
        for (int i = 0; i < ids.length; i++) ids[i] = merged.get(i).getChapterId();
        assertEquals(Arrays.asList("38623350", "38623354", "38623375", "38623378", "38623395", "38623396", "38623397"),
                Arrays.asList(ids));
    }

    @Test
    public void testLateTitledReuploadReplacesEarlyPlaceholder() {
        String[][] items = {
            {"38626100", "分节阅读第4207节"},
            {"38626103", "第四千二百零八章 至高组会议"},
            {"49080153", "第四千二百零七章 某章"},
            {"49080154", "分节阅读第4208节"},
        };
        List<TocEntry> merged = TocMerger.mergeTocPages(Arrays.asList(entries(items)));
        assertEquals(2, merged.size());
        assertEquals("49080153", merged.get(0).getChapterId());
        assertEquals("38626103", merged.get(1).getChapterId());
    }

    private static List<TocEntry> entries(String[][] items) {
        List<TocEntry> list = new java.util.ArrayList<TocEntry>(items.length);
        for (int i = 0; i < items.length; i++) list.add(new TocEntry("36780", items[i][0], items[i][1], i, false, 0));
        return list;
    }

    private static List<TocEntry> combine(List<TocEntry> a, List<TocEntry> b) {
        List<TocEntry> list = new java.util.ArrayList<TocEntry>(a.size() + b.size());
        list.addAll(a);
        list.addAll(b);
        return list;
    }
}
