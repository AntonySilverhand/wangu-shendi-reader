package org.wanshu.reader.core.text;

import java.util.List;
import org.wanshu.reader.core.model.ReadingAnchor;

public class AnchorMapper {

    public static int findBlockIndexForParagraph(List<TextBlock> blocks, int paragraphIndex) {
        if (blocks == null || blocks.isEmpty()) {
            return 0;
        }
        for (int i = 0; i < blocks.size(); i++) {
            TextBlock block = blocks.get(i);
            if (paragraphIndex >= block.getStartParagraphIndex() && paragraphIndex <= block.getEndParagraphIndex()) {
                return i;
            }
        }
        if (paragraphIndex > blocks.get(blocks.size() - 1).getEndParagraphIndex()) {
            return blocks.size() - 1;
        }
        return 0;
    }

    public static int getOffsetInBlock(TextBlock block, int paragraphIndex, int offsetInParagraph) {
        if (block == null) {
            return 0;
        }
        int relIndex = paragraphIndex - block.getStartParagraphIndex();
        int baseOffset = block.getParagraphStartOffset(relIndex);
        int totalOffset = baseOffset + offsetInParagraph;
        if (totalOffset < 0) {
            return 0;
        }
        if (totalOffset > block.getText().length()) {
            return block.getText().length();
        }
        return totalOffset;
    }

    public static ReadingAnchor mapBlockOffsetToAnchor(
            String bookId,
            String chapterId,
            TextBlock block,
            int blockCharOffset,
            long updatedAt
    ) {
        if (block == null) {
            return new ReadingAnchor(bookId, chapterId, 0, 0, updatedAt);
        }

        List<Integer> offsets = block.getParagraphStartOffsets();
        int matchedPara = block.getStartParagraphIndex();
        int matchedOffset = 0;

        for (int i = 0; i < offsets.size(); i++) {
            int paraStart = offsets.get(i);
            if (blockCharOffset >= paraStart) {
                matchedPara = block.getStartParagraphIndex() + i;
                matchedOffset = blockCharOffset - paraStart;
            } else {
                break;
            }
        }

        return new ReadingAnchor(bookId, chapterId, matchedPara, matchedOffset, updatedAt);
    }
}
