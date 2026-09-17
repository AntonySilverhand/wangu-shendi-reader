package org.wanshu.reader.core.toc;

import org.wanshu.reader.core.net.CancellationToken;

public class TocSearchTaskRunnable implements Runnable {
    private final TocSearchController controller;
    private final long generation;
    private final String query;
    private final boolean retryFirst;
    private final CancellationToken token;

    public TocSearchTaskRunnable(
            TocSearchController controller,
            long generation,
            String query,
            boolean retryFirst,
            CancellationToken token
    ) {
        this.controller = controller;
        this.generation = generation;
        this.query = query;
        this.retryFirst = retryFirst;
        this.token = token;
    }

    @Override
    public void run() {
        controller.executeTask(generation, query, retryFirst, token);
    }
}
