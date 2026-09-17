package org.wanshu.reader.core.search;

public class ChapterSearchMatch {
    private final int paragraphIndex;
    private final int startOffset;
    private final int endOffset;

    public ChapterSearchMatch(int paragraphIndex, int startOffset, int endOffset) {
        this.paragraphIndex = paragraphIndex;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
    }

    public int getParagraphIndex() {
        return paragraphIndex;
    }

    public int getStartOffset() {
        return startOffset;
    }

    public int getEndOffset() {
        return endOffset;
    }
}
