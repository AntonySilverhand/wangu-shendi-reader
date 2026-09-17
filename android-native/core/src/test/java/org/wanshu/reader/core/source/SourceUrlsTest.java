package org.wanshu.reader.core.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.junit.Test;

public class SourceUrlsTest {

    @Test
    public void testTocPath() {
        assertEquals("/book/36780/", SourceUrls.tocPath(1));
        assertEquals("/book/36780/1.html", SourceUrls.tocPath(2));
        assertEquals("/book/36780/43.html", SourceUrls.tocPath(44));
        try {
            SourceUrls.tocPath(0);
            fail("Should throw for page 0");
        } catch (IllegalArgumentException expected) {}
    }

    @Test
    public void testChapterPath() {
        assertEquals("/book/36780_38621328.html", SourceUrls.chapterPath("38621328", 0));
        assertEquals("/book/36780/38621328_1.html", SourceUrls.chapterPath("38621328", 1));
        assertEquals("https://wanshuge.org/book/36780/38621328_2.html", SourceUrls.buildChapterUrl("38621328", 2));

        try {
            SourceUrls.chapterPath("../../etc/passwd", 0);
            fail("Should throw for path traversal");
        } catch (IllegalArgumentException expected) {}

        try {
            SourceUrls.chapterPath("12", 40);
            fail("Should throw for pageIndex >= 40");
        } catch (IllegalArgumentException expected) {}
    }

    @Test
    public void testParseChapterLink() {
        assertEquals(new ChapterLinkResult("38621328", 0), SourceUrls.parseChapterLink("/book/36780_38621328.html"));
        assertEquals(new ChapterLinkResult("38621328", 1), SourceUrls.parseChapterLink("/book/36780/38621328_1.html"));
        assertEquals(new ChapterLinkResult("38621328", 0), SourceUrls.parseChapterLink("https://wanshuge.org/book/36780_38621328.html"));
        assertEquals(new ChapterLinkResult("38621328", 0), SourceUrls.parseChapterLink("https://wanshuge.org/book/36780_38621328.html?x=1#top"));
        assertEquals(new ChapterLinkResult("38621328", 1), SourceUrls.parseChapterLink("http://wanshuge.org/book/36780/38621328_1.html?from=pc"));
        assertEquals(new ChapterLinkResult("38621328", 2), SourceUrls.parseChapterLink("//wanshuge.org/book/36780/38621328_2.html"));

        // Evil/unrelated hosts and paths rejected
        assertNull(SourceUrls.parseChapterLink("https://evil.com/x"));
        assertNull(SourceUrls.parseChapterLink("https://wanshuge.org/book/99999_38621328.html"));
        assertNull(SourceUrls.parseChapterLink("javascript:void(0)"));
        assertNull(SourceUrls.parseChapterLink(""));
        assertNull(SourceUrls.parseChapterLink(null));
        // TOC pagination links rejected
        assertNull(SourceUrls.parseChapterLink("/book/36780/1.html"));
        assertNull(SourceUrls.parseChapterLink("/book/36780/43.html"));
    }
}
