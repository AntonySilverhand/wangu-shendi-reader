package org.wanshu.reader.ui.reader;

public class ReaderSearchQueryDebounceRunnable implements Runnable {
    private final ReaderController controller;
    private final String query;

    public ReaderSearchQueryDebounceRunnable(ReaderController controller, String query) {
        this.controller = controller;
        this.query = query != null ? query : "";
    }

    @Override
    public void run() {
        if (controller != null) {
            controller.onSearchQueryChanged(query);
        }
    }
}
