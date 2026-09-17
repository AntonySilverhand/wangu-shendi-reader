package org.wanshu.reader.core.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class ChapterSearchEngineTest {

    @Test
    public void testCaseInsensitiveSearch() {
        List<String> paragraphs = Arrays.asList(
                "张若尘看着眼前的神剑，心中震撼。",
                "神剑有灵，张若尘伸手握住剑柄。",
                "第三段没有匹配。"
        );

        List<ChapterSearchMatch> matches = ChapterSearchEngine.search(paragraphs, "张若尘");
        assertEquals(2, matches.size());

        assertEquals(0, matches.get(0).getParagraphIndex());
        assertEquals(0, matches.get(0).getStartOffset());
        assertEquals(3, matches.get(0).getEndOffset());

        assertEquals(1, matches.get(1).getParagraphIndex());
        assertEquals(5, matches.get(1).getStartOffset());
        assertEquals(8, matches.get(1).getEndOffset());
    }

    @Test
    public void testMultipleMatchesInSingleParagraph() {
        List<String> paragraphs = Arrays.asList(
                "天地不仁，以万物为刍狗；圣人不仁，以百姓为刍狗。"
        );

        List<ChapterSearchMatch> matches = ChapterSearchEngine.search(paragraphs, "刍狗");
        assertEquals(2, matches.size());
        assertEquals(0, matches.get(0).getParagraphIndex());
        assertEquals(0, matches.get(1).getParagraphIndex());
        assertTrue(matches.get(0).getStartOffset() < matches.get(1).getStartOffset());
    }

    @Test
    public void testEmptyQueryReturnsNoMatches() {
        List<String> paragraphs = Arrays.asList("测试段落");
        List<ChapterSearchMatch> matches = ChapterSearchEngine.search(paragraphs, "");
        assertEquals(0, matches.size());
    }
}
