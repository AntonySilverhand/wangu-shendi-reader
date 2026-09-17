package org.wanshu.reader.core.txt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class TxtSplitterTest {

    @Test
    public void testRecognizeCommonChapterHeadings() {
        String text = "第一章 起点\n" +
                "第一段内容。\n" +
                "第二段内容。\n" +
                "\n" +
                "第2章 转折\n" +
                "另一些内容。\n" +
                "番外第一章 后来\n" +
                "番外内容。";

        List<RawTxtChapter> chapters = TxtSplitter.split(text);
        assertEquals(3, chapters.size());

        assertEquals("第一章 起点", chapters.get(0).getTitle());
        assertEquals(2, chapters.get(0).getParagraphs().size());
        assertEquals("第一段内容。", chapters.get(0).getParagraphs().get(0));
        assertEquals("第二段内容。", chapters.get(0).getParagraphs().get(1));

        assertEquals("第2章 转折", chapters.get(1).getTitle());
        assertEquals(1, chapters.get(1).getParagraphs().size());
        assertEquals("另一些内容。", chapters.get(1).getParagraphs().get(0));

        assertEquals("番外第一章 后来", chapters.get(2).getTitle());
        assertEquals(1, chapters.get(2).getParagraphs().size());
        assertEquals("番外内容。", chapters.get(2).getParagraphs().get(0));
    }

    @Test
    public void testNoHeadingDefaultsToZhengWen() {
        String text = "只有一段文字。\n还有一段。";
        List<RawTxtChapter> chapters = TxtSplitter.split(text);

        assertEquals(1, chapters.size());
        assertEquals("正文", chapters.get(0).getTitle());
        assertEquals(2, chapters.get(0).getParagraphs().size());
        assertEquals("只有一段文字。", chapters.get(0).getParagraphs().get(0));
        assertEquals("还有一段。", chapters.get(0).getParagraphs().get(1));
    }

    @Test
    public void testChineseAndArabicNumeralsAndChapter() {
        String text = "第一百二十三章 标题\n内容\nChapter 5 Something\ncontent";
        List<RawTxtChapter> chapters = TxtSplitter.split(text);

        assertEquals(2, chapters.size());
        assertEquals("第一百二十三章 标题", chapters.get(0).getTitle());
        assertTrue(chapters.get(1).getTitle().contains("Chapter 5"));
    }
}
