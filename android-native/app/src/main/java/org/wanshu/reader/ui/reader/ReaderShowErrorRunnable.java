package org.wanshu.reader.ui.reader;

public class ReaderShowErrorRunnable implements Runnable {
    private final ReaderView view;
    private final String message;

    public ReaderShowErrorRunnable(ReaderView view, String message) {
        this.view = view;
        this.message = message;
    }

    @Override
    public void run() {
        view.showError(message);
    }
}
