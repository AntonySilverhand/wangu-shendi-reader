package org.wanshu.reader.ui.download;

import org.wanshu.reader.download.DownloadProgress;
import org.wanshu.reader.download.DownloadProgressListener;

public class DownloadDialogProgressListener implements DownloadProgressListener {
    private final DownloadsDialog dialog;

    public DownloadDialogProgressListener(DownloadsDialog dialog) {
        this.dialog = dialog;
    }

    @Override
    public void onDownloadProgress(DownloadProgress progress) {
        if (dialog != null) {
            dialog.onProgressUpdate(progress);
        }
    }
}
