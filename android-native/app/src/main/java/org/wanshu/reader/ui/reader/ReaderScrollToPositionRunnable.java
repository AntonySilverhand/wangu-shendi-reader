package org.wanshu.reader.ui.reader;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class ReaderScrollToPositionRunnable implements Runnable {
    private final RecyclerView recyclerView;
    private final int position;
    private final int offset;

    public ReaderScrollToPositionRunnable(RecyclerView recyclerView, int position, int offset) {
        this.recyclerView = recyclerView;
        this.position = position;
        this.offset = offset;
    }

    @Override
    public void run() {
        RecyclerView.LayoutManager lm = recyclerView.getLayoutManager();
        if (lm instanceof LinearLayoutManager) {
            ((LinearLayoutManager) lm).scrollToPositionWithOffset(position, offset);
        }
    }
}
