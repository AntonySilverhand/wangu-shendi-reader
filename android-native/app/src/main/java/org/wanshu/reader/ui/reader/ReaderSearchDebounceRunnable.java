package org.wanshu.reader.ui.reader;

public class ReaderSearchDebounceRunnable implements Runnable {
    private final ReaderController controller;
    private final String query;

    public ReaderSearchDebounceRunnable(ReaderController controller, String query) {
        this.controller = controller;
        this.query = query;
    }

    @Override
    public void run() {
        controller.executeSearch(query);
    }
}
