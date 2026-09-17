package org.wanshu.reader.core.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;

public class ReadingAnchorTest {

    @Test
    public void testBookKeyAndChapterKeyIsolation() {
        ChapterKey onlineKey = new ChapterKey("36780", "100");
        ChapterKey localKey = new ChapterKey("local-123", "100");

        assertNotEquals(onlineKey, localKey);
        assertEquals("36780:100", onlineKey.toString());
        assertEquals("local-123:100", localKey.toString());
        assertTrue(onlineKey.isOnlineBook());
        assertFalse(localKey.isOnlineBook());
    }

    @Test
    public void testReadingAnchorUtf16AndEmoji() {
        // String with surrogate pairs (Emoji)
        // ⚡ (1 code unit), 🐉 (2 code units, surrogate pair: \uD83D\uDC09)
        String text = "天地初开🐉万物生长";
        int emojiStartOffset = text.indexOf("🐉");
        int emojiEndOffset = emojiStartOffset + 2; // surrogate pair length is 2 UTF-16 code units

        assertEquals(4, emojiStartOffset);
        assertEquals(6, emojiEndOffset);

        ReadingAnchor anchor = new ReadingAnchor(
                "36780",
                "100",
                2,
                emojiStartOffset,
                1700000000000L,
                "hash123",
                "天地初开🐉",
                1L
        );

        assertEquals("36780", anchor.getBookId());
        assertEquals("100", anchor.getChapterId());
        assertEquals(2, anchor.getParagraphIndex());
        assertEquals(4, anchor.getOffsetUtf16());
        assertEquals(1700000000000L, anchor.getUpdatedAt());
        assertEquals("hash123", anchor.getParagraphHash());
        assertEquals("天地初开🐉", anchor.getQuote());
        assertEquals(1L, anchor.getContentRevision());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNegativeParagraphIndexThrows() {
        new ReadingAnchor("36780", "1", -1, 0, 0L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNegativeOffsetThrows() {
        new ReadingAnchor("36780", "1", 0, -1, 0L);
    }

    @Test
    public void testChapterResultSemanticsVersion() {
        ChapterResult result = new ChapterResult(
                "36780",
                "1",
                "第一章",
                Arrays.asList("段落1", "段落2"),
                null,
                "2",
                1,
                6,
                true,
                Arrays.<Integer>asList()
        );

        assertEquals(ChapterResult.CURRENT_SOURCE_SEMANTICS_VERSION, result.getSourceSemanticsVersion());
        assertTrue(result.isComplete());
        assertEquals(2, result.getParagraphs().size());
        assertEquals("2", result.getNextChapterId());
    }
}
