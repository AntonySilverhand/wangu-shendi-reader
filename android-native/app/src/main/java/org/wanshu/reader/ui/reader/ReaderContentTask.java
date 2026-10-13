package org.wanshu.reader.ui.reader;

import java.util.List;
import org.wanshu.reader.core.model.ChapterResult;
import org.wanshu.reader.core.text.TextBlock;

/** Parse/render work is bounded off-main; generation is checked again at actual UI delivery. */
public final class ReaderContentTask implements Runnable {
    private final ReaderController controller;
    private final ChapterResult chapter;
    private final long generation;
    private final boolean complete;
    private List<TextBlock> blocks;
    public ReaderContentTask(ReaderController controller, ChapterResult chapter, long generation, boolean complete) {
        this.controller = controller; this.chapter = chapter; this.generation = generation; this.complete = complete;
    }
    @Override public void run() {
        if (blocks == null) {
            blocks = controller.buildBlocks(chapter);
            controller.postToUi(this);
        } else controller.applyChapter(chapter, blocks, generation, complete);
    }
}
