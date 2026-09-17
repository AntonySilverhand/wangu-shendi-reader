package org.wanshu.reader.ui.reader;

public class ReaderShowLoadingRunnable implements Runnable {
    private final ReaderView view;

    public ReaderShowLoadingRunnable(ReaderView view) {
        this.view = view;
    }

    @Override
    public void run() {
        view.showLoading();
    }
}
