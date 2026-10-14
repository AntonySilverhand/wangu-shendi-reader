package org.wanshu.reader.core.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class SourceUrlsTest {

    private static final String B = "36780";

    @Test
    public void testTocPath() {
        assertEquals("/book/36780/", SourceUrls.tocPath(B, 1));
        assertEquals("/book/36780/1.html", SourceUrls.tocPath(B, 2));
        assertEquals("/book/36780/43.html", SourceUrls.tocPath(B, 44));
        try {
            SourceUrls.tocPath(B, 0);
            fail("Should throw for page 0");
        } catch (IllegalArgumentException expected) {}
    }

    @Test
    public void testChapterPath() {
        assertEquals("/book/36780_38621328.html", SourceUrls.chapterPath(B, "38621328", 0));
        assertEquals("/book/36780/38621328_1.html", SourceUrls.chapterPath(B, "38621328", 1));
        assertEquals("https://wanshuge.org/book/36780/38621328_2.html", SourceUrls.buildChapterUrl(B, "38621328", 2));

        try {
            SourceUrls.chapterPath(B, "../../etc/passwd", 0);
            fail("Should throw for path traversal");
        } catch (IllegalArgumentException expected) {}

        try {
            SourceUrls.chapterPath(B, "12", 40);
            fail("Should throw for pageIndex >= 40");
        } catch (IllegalArgumentException expected) {}
    }

    @Test
    public void testOtherBookIds() {
        assertEquals("/book/104429/", SourceUrls.tocPath("104429", 1));
        assertEquals("/book/104429/16.html", SourceUrls.tocPath("104429", 17));
        assertEquals("/book/230580_72836267.html", SourceUrls.chapterPath("230580", "72836267", 0));
        assertEquals("/book/230580/72836267_2.html", SourceUrls.chapterPath("230580", "72836267", 2));
        assertEquals("https://wanshuge.org/img/104429.jpg", SourceUrls.coverUrl("104429"));
        assertEquals("https://wanshuge.org/search.html", SourceUrls.SEARCH_URL);
    }

    @Test
    public void testBookIdValidation() {
        assertTrue(SourceUrls.isValidBookId("1"));
        assertTrue(SourceUrls.isValidBookId("36780"));
        assertTrue(SourceUrls.isValidBookId("123456789"));
        String[] bad = new String[] {"", "0", "01", "abc", "1/../2", "1234567890", "36780?x", null, " 36780"};
        for (int i = 0; i < bad.length; i++) {
            assertFalse("should be invalid: " + bad[i], SourceUrls.isValidBookId(bad[i]));
            try {
                SourceUrls.tocPath(bad[i], 1);
                fail("tocPath must reject " + bad[i]);
            } catch (IllegalArgumentException expected) {}
            try {
                SourceUrls.coverUrl(bad[i]);
                fail("coverUrl must reject " + bad[i]);
            } catch (IllegalArgumentException expected) {}
        }
    }

    @Test
    public void testParseChapterLink() {
        assertEquals(new ChapterLinkResult("38621328", 0), SourceUrls.parseChapterLink(B, "/book/36780_38621328.html"));
        assertEquals(new ChapterLinkResult("38621328", 1), SourceUrls.parseChapterLink(B, "/book/36780/38621328_1.html"));
        assertEquals(new ChapterLinkResult("38621328", 0), SourceUrls.parseChapterLink(B, "https://wanshuge.org/book/36780_38621328.html"));
        assertEquals(new ChapterLinkResult("38621328", 0), SourceUrls.parseChapterLink(B, "https://www.wanshuge.org/book/36780_38621328.html?x=1#top"));
        assertEquals(new ChapterLinkResult("38621328", 1), SourceUrls.parseChapterLink(B, "http://wanshuge.org/book/36780/38621328_1.html?from=pc"));
        assertEquals(new ChapterLinkResult("38621328", 2), SourceUrls.parseChapterLink(B, "//wanshuge.org/book/36780/38621328_2.html"));

        // Evil/unrelated hosts and paths rejected
        assertNull(SourceUrls.parseChapterLink(B, "https://evil.com/x"));
        assertNull(SourceUrls.parseChapterLink(B, "https://wanshuge.org/book/99999_38621328.html"));
        assertNull(SourceUrls.parseChapterLink(B, "https://example.com/book/36780_38621328.html"));
        assertNull(SourceUrls.parseChapterLink(B, "javascript:void(0)"));
        assertNull(SourceUrls.parseChapterLink(B, ""));
        assertNull(SourceUrls.parseChapterLink(B, null));
        // TOC pagination links rejected
        assertNull(SourceUrls.parseChapterLink(B, "/book/36780/1.html"));
        assertNull(SourceUrls.parseChapterLink(B, "/book/36780/43.html"));
    }

    @Test
    public void testParseChapterLinkOnlyAcceptsOwnBook() {
        assertEquals(new ChapterLinkResult("21930803", 0), SourceUrls.parseChapterLink("104429", "/book/104429_21930803.html"));
        assertEquals(new ChapterLinkResult("21930803", 1), SourceUrls.parseChapterLink("104429", "/book/104429/21930803_1.html"));
        assertNull(SourceUrls.parseChapterLink("104429", "/book/36780_21930803.html"));
        assertNull(SourceUrls.parseChapterLink("36780", "/book/104429_21930803.html"));
        assertNull(SourceUrls.parseChapterLink("104429", "/book/104429/16.html"));
        assertNull(SourceUrls.parseChapterLink("bad", "/book/bad_1.html"));
    }
}
