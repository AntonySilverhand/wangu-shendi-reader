package org.wanshu.reader.core.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.wanshu.reader.core.model.BookInfo;
import org.wanshu.reader.core.model.ChapterResult;
import org.wanshu.reader.core.model.TocEntry;
import org.wanshu.reader.core.util.JavaBase64Decoder;

/** Mirrors the multi-book cases of test/source.test.ts with the same real-page fixtures. */
public class MultiBookParserTest {

    private static final JavaBase64Decoder DECODER = new JavaBase64Decoder();

    private static String fixture(String name) {
        return FixtureHelper.readFixture(name);
    }

    @Test
    public void totalPagesComeFromTheSelector() {
        assertEquals(Integer.valueOf(17), SourceParser.parseTocTotalPages("104429", fixture("book-104429-p2.html")));
        assertEquals(Integer.valueOf(2), SourceParser.parseTocTotalPages("14773", fixture("toc-14773-p2.html")));
        assertEquals(Integer.valueOf(44), SourceParser.parseTocTotalPages("36780", fixture("toc-page2.html")));
    }

    @Test
    public void singleItemSelectorMeansOnePage() {
        assertEquals(Integer.valueOf(1), SourceParser.parseTocTotalPages("230580", fixture("book-230580-p2.html")));
    }

    @Test
    public void detailPageHasUnknownTotal() {
        assertNull(SourceParser.parseTocTotalPages("104429", fixture("book-104429-p1.html")));
        assertNull(SourceParser.parseTocTotalPages("36780", fixture("toc-page1.html")));
    }

    @Test
    public void totalPagesNeverLookAtOtherBooks() {
        // the 104429 selector says nothing about book 36780
        assertNull(SourceParser.parseTocTotalPages("36780", fixture("book-104429-p2.html")));
    }

    @Test
    public void singlePageTocMergesWithItsLoopbackPageInUploadOrder() {
        List<TocItemRaw> p1 = SourceParser.parseTocPageHtml("230580", fixture("book-230580-p1.html"), 1);
        List<TocItemRaw> p2 = SourceParser.parseTocPageHtml("230580", fixture("book-230580-p2.html"), 2);
        ClassifiedToc c1 = SourceParser.classifyTocItems("230580", p1, 1);
        ClassifiedToc c2 = SourceParser.classifyTocItems("230580", p2, 2);
        List<TocEntry> merged = TocMerger.mergeTocPages(Arrays.asList(
                combine(c1.getMain(), c1.getExtras()),
                combine(c2.getMain(), c2.getExtras())
        ));

        List<String> titles = new ArrayList<String>();
        for (int i = 0; i < merged.size(); i++) {
            assertEquals("230580", merged.get(i).getBookId());
            titles.add(TitleNormalizer.normalizeTitle(merged.get(i).getTitle()).getDisplayTitle());
        }
        List<String> expected = new ArrayList<String>();
        expected.add("人物介绍");
        for (int i = 1; i <= 16; i++) {
            expected.add("第" + i + "章");
        }
        assertEquals(expected, titles);
    }

    @Test
    public void tocEntriesCarryTheirBookId() {
        List<TocItemRaw> raw = SourceParser.parseTocPageHtml("104429", fixture("book-104429-p2.html"), 2);
        assertFalse(raw.isEmpty());
        ClassifiedToc classified = SourceParser.classifyTocItems("104429", raw, 2);
        assertFalse(classified.getMain().isEmpty());
        assertEquals("104429", classified.getMain().get(0).getBookId());
    }

    @Test
    public void bookInfoFromDetailPageMeta() {
        BookInfo a = SourceParser.parseBookInfo("104429", fixture("book-104429-p1.html"));
        assertNotNull(a);
        assertEquals("104429", a.getId());
        assertEquals("斗破苍穹", a.getTitle());
        assertEquals("九支书竹", a.getAuthor());
        assertEquals("连载中", a.getStatus());
        assertEquals("21930717", a.getFirstChapterId());
        assertNotNull(a.getIntro());
        assertTrue(a.getIntro().startsWith("三十三诸天世界"));

        BookInfo b = SourceParser.parseBookInfo("230580", fixture("book-230580-p1.html"));
        assertNotNull(b);
        assertEquals("万古神帝之万古青鹿传", b.getTitle());
        assertNull("only boilerplate, so no intro", b.getIntro());
        assertEquals("72836267", b.getFirstChapterId());

        BookInfo c = SourceParser.parseBookInfo("36780", fixture("toc-page1.html"));
        assertNotNull(c);
        assertEquals("8924760", c.getFirstChapterId());
    }

    @Test
    public void bookInfoNullWithoutMeta() {
        assertNull(SourceParser.parseBookInfo("36780", "<html><body>blocked</body></html>"));
    }

    @Test
    public void bareParagraphBodyIsParsedWithoutRecommendationLinks() {
        RawChapterPage page = SourceParser.parseChapterPage("230580", fixture("chapter-230580-72836267-p1.html"), "72836267", 0, DECODER);
        assertEquals("人物介绍", page.getTitle());
        assertTrue(page.getParagraphs().size() >= 5);
        assertTrue(page.getParagraphs().get(0).startsWith("青鹿神王"));
        StringBuilder all = new StringBuilder();
        for (int i = 0; i < page.getParagraphs().size(); i++) {
            all.append(page.getParagraphs().get(i));
        }
        assertFalse(all.toString().contains("请勿开启"));
        assertFalse(all.toString().contains("万书阁"));
        assertEquals("72836268", page.getNextChapterId());
        assertFalse(page.isRestricted());
    }

    @Test
    public void restrictedNoticeIsFlaggedAndKeptOutOfTheBody() {
        RawChapterPage page = SourceParser.parseChapterPage("230580", fixture("chapter-230580-89420896-vip.html"), "89420896", 0, DECODER);
        assertTrue(page.isRestricted());
        assertEquals(5, page.getParagraphs().size());
        StringBuilder all = new StringBuilder();
        for (int i = 0; i < page.getParagraphs().size(); i++) {
            all.append(page.getParagraphs().get(i));
        }
        assertFalse(all.toString().contains("VIP"));
        assertFalse(all.toString().contains("后续内容已被隐藏"));
        assertEquals("89420897", page.getNextChapterId());
    }

    @Test
    public void assemblerPassesRestrictedThrough() {
        MockPageFetcher fetcher = new MockPageFetcher("230580");
        fetcher.addPage(0, fixture("chapter-230580-89420896-vip.html"));
        ChapterResult result = new ChapterAssembler(fetcher).assemble("230580", "89420896");
        assertTrue(result.isRestricted());
        assertTrue(result.isComplete());
        assertEquals(5, result.getParagraphs().size());
        assertEquals("230580", result.getBookId());
    }

    @Test
    public void unrestrictedChapterIsNotFlagged() {
        MockPageFetcher fetcher = new MockPageFetcher("230580");
        fetcher.addPage(0, fixture("chapter-230580-72836267-p1.html"));
        ChapterResult result = new ChapterAssembler(fetcher).assemble("230580", "72836267");
        assertFalse(result.isRestricted());
    }

    @Test
    public void encodedBodyOfAnotherBookStillWorks() {
        RawChapterPage page = SourceParser.parseChapterPage("104429", fixture("chapter-104429-21930803-p1.html"), "21930803", 0, DECODER);
        assertEquals("第八章吐血的莫小狼", page.getTitle());
        assertTrue(page.getParagraphs().size() > 20);
        assertEquals(Arrays.asList(1), page.getSameChapterPages());
        assertEquals("21930792", page.getPrevChapterId());
    }

    @Test
    public void linksToOtherBooksAreIgnoredInChapterPages() {
        // Same page parsed as if it belonged to book 36780: none of its book-104429 links count.
        RawChapterPage page = SourceParser.parseChapterPage("36780", fixture("chapter-104429-21930803-p1.html"), "21930803", 0, DECODER);
        assertTrue(page.getSameChapterPages().isEmpty());
        assertNull(page.getPrevChapterId());
        assertNull(page.getNextChapterId());
    }

    @Test
    public void titleFromTitleTagDropsAnyTrailingAuthorBrackets() {
        String html = "<html><head><title>第12章 开始(某某作者)_万书阁</title></head><body></body></html>";
        assertEquals("第12章 开始", SourceParser.extractTitle(html));
        String fullWidth = "<html><head><title>第12章 开始（某某作者）_万书阁</title></head></html>";
        assertEquals("第12章 开始", SourceParser.extractTitle(fullWidth));
        String pageSuffix = "<html><head><title>第12章 开始(飞天鱼)第2页_万书阁</title></head></html>";
        assertEquals("第12章 开始", SourceParser.extractTitle(pageSuffix));
    }

    @Test
    public void inlineParagraphsStopAtPromotionAndSkipLinkedParagraphs() {
        String html = "<h2 class=\"chapter-title\">标题</h2>"
                + "<div class=\"read_btn\"><a href=\"/x\">上一章</a></div>"
                + "<p>第一段</p><p>推荐 <a href=\"/book/1/\">别的书</a></p><p>第二段</p>"
                + "<p style=\"color:red\">本站推广</p><p>不应出现</p>"
                + "<div class=\"read_btn\"></div>";
        List<String> paras = SourceParser.extractInlineParagraphs(html);
        assertEquals(Arrays.asList("第一段", "第二段"), paras);
    }

    @Test
    public void cleanIntroRemovesBoilerplate() {
        assertEquals("", SourceParser.cleanIntro("三秒记住本站，www.example.com", ""));
        assertEquals("正文简介。", SourceParser.cleanIntro("正文简介。三秒记住本站地址", ""));
        assertEquals("", SourceParser.cleanIntro("万古神帝之万古青鹿传", "万古神帝之万古青鹿传"));
        assertEquals("故事开始", SourceParser.cleanIntro("关于测试书：故事开始", "测试书"));
    }

    private static List<TocEntry> combine(List<TocEntry> a, List<TocEntry> b) {
        List<TocEntry> list = new ArrayList<TocEntry>(a.size() + b.size());
        list.addAll(a);
        list.addAll(b);
        return list;
    }
}
