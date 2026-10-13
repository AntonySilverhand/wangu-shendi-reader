package org.wanshu.reader.ui.reader;

import java.util.List;
import org.wanshu.reader.core.search.ChapterSearchEngine;
import org.wanshu.reader.core.search.ChapterSearchPage;
import org.wanshu.reader.core.net.CancellationToken;

public final class ReaderSearchTask implements Runnable {
    private final ReaderController controller;
    private final List<String> paragraphs;
    private final String query;
    private final long generation;
    private final int windowStart, targetIndex;
    private final CancellationToken token;
    private ChapterSearchPage page;
    public ReaderSearchTask(ReaderController controller, List<String> paragraphs, String query, int windowStart,
            int targetIndex, long generation, CancellationToken token) {
        this.controller = controller; this.paragraphs = paragraphs; this.query = query; this.generation = generation;
        this.windowStart = windowStart; this.targetIndex = targetIndex; this.token = token;
    }
    @Override public void run() {
        if (token.isCancelled()) return;
        if (page == null) {
            page = ChapterSearchEngine.searchPage(paragraphs, query, windowStart, token);
            if (!token.isCancelled()) controller.postToUi(this);
        } else controller.applySearchResults(page, targetIndex, generation);
    }
}
