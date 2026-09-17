package org.wanshu.reader.ui.download;

import android.view.View;

public class DownloadCancelClickListener implements View.OnClickListener {
    private final DownloadsDialog dialog;

    public DownloadCancelClickListener(DownloadsDialog dialog) {
        this.dialog = dialog;
    }

    @Override
    public void onClick(View v) {
        if (dialog != null) {
            dialog.cancelDownload();
        }
    }
}
