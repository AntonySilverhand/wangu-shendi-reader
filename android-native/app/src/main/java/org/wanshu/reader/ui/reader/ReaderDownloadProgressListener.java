package org.wanshu.reader.ui.reader;

import org.wanshu.reader.download.DownloadProgress;
import org.wanshu.reader.download.DownloadProgressListener;

public class ReaderDownloadProgressListener implements DownloadProgressListener {
    private final ReaderController controller;

    public ReaderDownloadProgressListener(ReaderController controller) {
        this.controller = controller;
    }

    @Override
    public void onDownloadProgress(DownloadProgress progress) {
        if (controller != null) {
            controller.onDownloadProgress(progress);
        }
    }
}
