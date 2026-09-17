package org.wanshu.reader.ui.reader;

import java.util.List;
import org.wanshu.reader.core.text.TextBlock;

public class ReaderShowContentRunnable implements Runnable {
    private final ReaderView view;
    private final String title;
    private final List<TextBlock> blocks;
    private final boolean hasPrev;
    private final boolean hasNext;
    private final int targetParagraph;

    public ReaderShowContentRunnable(
            ReaderView view,
            String title,
            List<TextBlock> blocks,
            boolean hasPrev,
            boolean hasNext,
            int targetParagraph
    ) {
        this.view = view;
        this.title = title;
        this.blocks = blocks;
        this.hasPrev = hasPrev;
        this.hasNext = hasNext;
        this.targetParagraph = targetParagraph;
    }

    @Override
    public void run() {
        view.showContent(title, blocks, hasPrev, hasNext, targetParagraph);
    }
}
