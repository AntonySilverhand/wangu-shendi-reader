package org.wanshu.reader.core.text;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.wanshu.reader.core.model.ReadingAnchor;

public class TextBlockBuilderTest {

    @Test
    public void testBlocksMerging() {
        List<String> paragraphs = new ArrayList<String>();
        for (int i = 0; i < 50; i++) {
            paragraphs.add("第 " + i + " 段正文：修罗剑意，横扫八方。天地异象纷呈，诸神俯首。");
        }

        TextBlockBuilder builder = new TextBlockBuilder(500);
        List<TextBlock> blocks = builder.buildBlocks(paragraphs);

        assertTrue(blocks.size() > 1);
        assertEquals(0, blocks.get(0).getStartParagraphIndex());
        assertEquals(49, blocks.get(blocks.size() - 1).getEndParagraphIndex());
    }

    @Test
    public void testAnchorMappingBidirectional() {
        List<String> paragraphs = new ArrayList<String>();
        paragraphs.add("第一段：晨曦破晓。");
        paragraphs.add("第二段：雷电交加，神龙啸天。🐉");
        paragraphs.add("第三段：群山寂静。");

        TextBlockBuilder builder = new TextBlockBuilder(2000);
        List<TextBlock> blocks = builder.buildBlocks(paragraphs);

        assertEquals(1, blocks.size());
        TextBlock block = blocks.get(0);

        // Map para 1, char 5 ("神龙啸天。🐉" starts at 5)
        int blockOffset = AnchorMapper.getOffsetInBlock(block, 1, 5);
        ReadingAnchor restored = AnchorMapper.mapBlockOffsetToAnchor("36780", "1", block, blockOffset, 1000L);

        assertEquals(1, restored.getParagraphIndex());
        assertEquals(5, restored.getOffsetUtf16());
    }
}
