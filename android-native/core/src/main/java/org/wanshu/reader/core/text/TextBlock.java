package org.wanshu.reader.core.text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TextBlock {
    private final int blockIndex;
    private final int startParagraphIndex;
    private final int endParagraphIndex;
    private final String text;
    private final List<Integer> paragraphStartOffsets;

    public TextBlock(
            int blockIndex,
            int startParagraphIndex,
            int endParagraphIndex,
            String text,
            List<Integer> paragraphStartOffsets
    ) {
        this.blockIndex = blockIndex;
        this.startParagraphIndex = startParagraphIndex;
        this.endParagraphIndex = endParagraphIndex;
        this.text = text != null ? text : "";
        this.paragraphStartOffsets = paragraphStartOffsets != null
                ? Collections.unmodifiableList(new ArrayList<Integer>(paragraphStartOffsets))
                : Collections.<Integer>emptyList();
    }

    public int getBlockIndex() {
        return blockIndex;
    }

    public int getStartParagraphIndex() {
        return startParagraphIndex;
    }

    public int getEndParagraphIndex() {
        return endParagraphIndex;
    }

    public String getText() {
        return text;
    }

    public List<Integer> getParagraphStartOffsets() {
        return paragraphStartOffsets;
    }

    public int getParagraphStartOffset(int relativeIndex) {
        if (relativeIndex >= 0 && relativeIndex < paragraphStartOffsets.size()) {
            return paragraphStartOffsets.get(relativeIndex);
        }
        return 0;
    }
}
