package org.wanshu.reader.core.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.wanshu.reader.core.model.ChapterResult;

public class ChapterAssemblerTest {

    @Test
    public void testChapter13375259OldTemplate() {
        MockPageFetcher fetcher = new MockPageFetcher();
        fetcher.addPage(0, FixtureHelper.readFixture("chapter-13375259-p1.html"));

        ChapterAssembler assembler = new ChapterAssembler(fetcher);
        ChapterResult result = assembler.assemble(SourceUrls.BOOK_ID, "13375259");

        assertEquals("13375259", result.getChapterId());
        assertTrue(result.getParagraphs().size() > 0);
        assertEquals(1, result.getPageCount());
        assertTrue(result.isComplete());
    }

    @Test
    public void testChapter8924760SinglePage() {
        MockPageFetcher fetcher = new MockPageFetcher();
        fetcher.addPage(0, FixtureHelper.readFixture("chapter-8924760-p1.html"));

        ChapterAssembler assembler = new ChapterAssembler(fetcher);
        ChapterResult result = assembler.assemble(SourceUrls.BOOK_ID, "8924760");

        assertEquals("8924760", result.getChapterId());
        assertTrue(result.getParagraphs().size() > 0);
        assertTrue(result.isComplete());
    }

    @Test
    public void testChapter38621328ThreePages() {
        MockPageFetcher fetcher = new MockPageFetcher();
        fetcher.addPage(0, FixtureHelper.readFixture("chapter-38621328-p1.html"));
        fetcher.addPage(1, FixtureHelper.readFixture("chapter-38621328-p2.html"));
        fetcher.addPage(2, FixtureHelper.readFixture("chapter-38621328-p3.html"));

        ChapterAssembler assembler = new ChapterAssembler(fetcher);
        ChapterResult result = assembler.assemble(SourceUrls.BOOK_ID, "38621328");

        assertEquals("38621328", result.getChapterId());
        assertEquals(3, result.getPageCount());
        assertTrue(result.isComplete());
        assertEquals("38621330", result.getNextChapterId());
    }

    @Test
    public void testChapter38621571Loopback() {
        MockPageFetcher fetcher = new MockPageFetcher();
        fetcher.addPage(0, FixtureHelper.readFixture("chapter-38621571-p1.html"));
        fetcher.addPage(1, FixtureHelper.readFixture("chapter-38621571-p2.html"));
        fetcher.addPage(2, FixtureHelper.readFixture("chapter-38621571-p3.html"));
        fetcher.addPage(3, FixtureHelper.readFixture("chapter-38621571-loopback.html"));

        ChapterAssembler assembler = new ChapterAssembler(fetcher);
        ChapterResult result = assembler.assemble(SourceUrls.BOOK_ID, "38621571");

        assertEquals("38621571", result.getChapterId());
        assertEquals(3, result.getPageCount());
        assertTrue(result.isComplete());
        assertEquals("38621574", result.getNextChapterId());
    }

    @Test
    public void testChapter38626103FivePages() {
        MockPageFetcher fetcher = new MockPageFetcher();
        fetcher.addPage(0, FixtureHelper.readFixture("chapter-38626103-p1.html"));
        fetcher.addPage(1, FixtureHelper.readFixture("chapter-38626103-p2.html"));
        fetcher.addPage(2, FixtureHelper.readFixture("chapter-38626103-p3.html"));
        fetcher.addPage(3, FixtureHelper.readFixture("chapter-38626103-p4.html"));
        fetcher.addPage(4, FixtureHelper.readFixture("chapter-38626103-p5.html"));

        ChapterAssembler assembler = new ChapterAssembler(fetcher);
        ChapterResult result = assembler.assemble(SourceUrls.BOOK_ID, "38626103");

        assertEquals("38626103", result.getChapterId());
        assertEquals(5, result.getPageCount());
        assertTrue(result.isComplete());
        assertTrue(result.getMissingPages().isEmpty());
    }

    @Test
    public void testChapter38626103Page4Failed() {
        MockPageFetcher fetcher = new MockPageFetcher();
        fetcher.addPage(0, FixtureHelper.readFixture("chapter-38626103-p1.html"));
        fetcher.addPage(1, FixtureHelper.readFixture("chapter-38626103-p2.html"));
        fetcher.addPage(2, FixtureHelper.readFixture("chapter-38626103-p3.html"));
        fetcher.addFailure(3); // page 4 (index 3) fails
        fetcher.addPage(4, FixtureHelper.readFixture("chapter-38626103-p5.html"));

        ChapterAssembler assembler = new ChapterAssembler(fetcher);
        ChapterResult result = assembler.assemble(SourceUrls.BOOK_ID, "38626103");

        assertEquals("38626103", result.getChapterId());
        assertEquals(4, result.getPageCount()); // 0, 1, 2, 4
        assertFalse(result.isComplete());
        assertEquals(1, result.getMissingPages().size());
        assertEquals(Integer.valueOf(3), result.getMissingPages().get(0));
    }

    @Test
    public void testIntraPageDuplicatePreserved() {
        // Direct MockFetcher returning synthetic RawChapterPage
        PageFetcher syntheticFetcher = new SyntheticPageFetcher(
                Arrays.asList(
                        new RawChapterPage("999", 0, "第九百九十九章", Arrays.asList("第一段", "“杀！”", "“杀！”", "“杀！”", "末段"), null, null, 1, Arrays.asList(1), 0),
                        new RawChapterPage("999", 1, "第九百九十九章", Arrays.asList("末段", "后文开始"), null, "1000", null, Arrays.<Integer>asList(), 1)
                )
        );

        ChapterAssembler assembler = new ChapterAssembler(syntheticFetcher);
        ChapterResult result = assembler.assemble(SourceUrls.BOOK_ID, "999");

        List<String> paras = result.getParagraphs();
        // Boundary "末段" deduplicated once
        // Intra-page "“杀！”" three times preserved!
        assertEquals(6, paras.size());
        assertEquals("第一段", paras.get(0));
        assertEquals("“杀！”", paras.get(1));
        assertEquals("“杀！”", paras.get(2));
        assertEquals("“杀！”", paras.get(3));
        assertEquals("末段", paras.get(4));
        assertEquals("后文开始", paras.get(5));
        assertTrue(result.isComplete());
    }

    @Test
    public void testNonAdjacentMissingPageRepetitionNotDeduplicated() {
        SyntheticPageFetcher gapFetcher = new SyntheticPageFetcher(
                Arrays.asList(
                        new RawChapterPage("888", 0, "测试", Arrays.asList("段落A", "段落B"), null, null, 1, Arrays.asList(1, 2), 0),
                        // Page 1 is missing/failed
                        new RawChapterPage("888", 2, "测试", Arrays.asList("段落B", "段落C"), null, "889", null, Arrays.<Integer>asList(), 2)
                )
        );
        gapFetcher.addFailure(1);

        ChapterAssembler assembler = new ChapterAssembler(gapFetcher);
        ChapterResult result = assembler.assemble(SourceUrls.BOOK_ID, "888");

        List<String> paras = result.getParagraphs();
        // Since Page 1 is missing, Page 0 and Page 2 are NOT adjacent -> "段落B" is NOT deduplicated!
        assertEquals(4, paras.size());
        assertEquals("段落A", paras.get(0));
        assertEquals("段落B", paras.get(1));
        assertEquals("段落B", paras.get(2));
        assertEquals("段落C", paras.get(3));
        assertFalse(result.isComplete());
        assertEquals(Arrays.asList(1), result.getMissingPages());
    }
}
