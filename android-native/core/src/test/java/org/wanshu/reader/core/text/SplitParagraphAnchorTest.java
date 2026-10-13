package org.wanshu.reader.core.text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.wanshu.reader.core.model.ReadingAnchor;
import static org.junit.Assert.*;

public class SplitParagraphAnchorTest {
    private List<String> paragraphs() {
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 99; i++) longText.append('字');
        longText.append("😀"); // Would straddle a naive 100-char block boundary.
        for (int i = 0; i < 1000; i++) longText.append("正文🐉");
        return Arrays.asList("开头", longText.toString(), "结尾");
    }

    @Test public void anchorsRoundTripAcrossChunksAndBlockSizes() {
        List<String> paragraphs = paragraphs();
        int[] sizes = {100, 200, 2000};
        int[] offsets = {0, 98, 99, 100, 101, 199, 200, 2500, paragraphs.get(1).length()};
        for (int size : sizes) {
            List<TextBlock> blocks = new TextBlockBuilder(size).buildBlocks(paragraphs);
            for (int offset : offsets) {
                int index = AnchorMapper.findBlockIndexForAnchor(blocks, 1, offset);
                TextBlock block = blocks.get(index);
                int local = AnchorMapper.getOffsetInBlock(block, 1, offset);
                ReadingAnchor restored = AnchorMapper.mapBlockOffsetToAnchor("local-test", "1", block, local, 1);
                assertEquals(1, restored.getParagraphIndex());
                assertEquals("size=" + size + ", offset=" + offset, offset, restored.getOffsetUtf16());
            }
        }
    }

    @Test public void cachedChunksRestoreOriginalParagraphsWithoutNewStorageFields() {
        List<String> original = paragraphs();
        List<TextBlock> persisted = new ArrayList<TextBlock>();
        for (TextBlock block : new TextBlockBuilder(100).buildBlocks(original)) {
            // Room v1 stores these fields, but not the in-memory paragraph offsets.
            persisted.add(new TextBlock(block.getBlockIndex(), block.getStartParagraphIndex(),
                    block.getEndParagraphIndex(), block.getText(), null));
        }
        assertEquals(original, TextBlockBuilder.restoreParagraphs(persisted));
    }

    @Test public void exactBoundarySelectsFollowingChunkAndKeepsEmojiIntact() {
        List<TextBlock> blocks = new TextBlockBuilder(100).buildBlocks(paragraphs());
        int index = AnchorMapper.findBlockIndexForAnchor(blocks, 1, 99);
        assertTrue(blocks.get(index).getText().startsWith("😀"));
        assertEquals(0, AnchorMapper.getOffsetInBlock(blocks.get(index), 1, 99));
        for (TextBlock block : blocks) {
            String text = block.getText();
            assertFalse(Character.isLowSurrogate(text.charAt(0)));
            assertFalse(Character.isHighSurrogate(text.charAt(text.length() - 1)));
        }
    }
}
