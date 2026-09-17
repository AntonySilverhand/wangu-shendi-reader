package org.wanshu.reader.core.toc;

public class TocSearchDebounceRunnable implements Runnable {
    private final TocSearchController controller;
    private final long generation;
    private final String query;

    public TocSearchDebounceRunnable(TocSearchController controller, long generation, String query) {
        this.controller = controller;
        this.generation = generation;
        this.query = query;
    }

    @Override
    public void run() {
        controller.onDebounceFired(generation, query);
    }
}
