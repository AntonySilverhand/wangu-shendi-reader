package org.wanshu.reader.ui.reader;

public class ReaderDownloadStatusRunnable implements Runnable {
    private final ReaderView view;
    private final String status;

    public ReaderDownloadStatusRunnable(ReaderView view, String status) {
        this.view = view;
        this.status = status;
    }

    @Override
    public void run() {
        if (view != null) {
            view.setDownloadStatus(status);
        }
    }
}
