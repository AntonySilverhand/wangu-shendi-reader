package org.wanshu.reader.ui.download;

import android.view.View;

public class DownloadStartClickListener implements View.OnClickListener {
    private final DownloadsDialog dialog;

    public DownloadStartClickListener(DownloadsDialog dialog) {
        this.dialog = dialog;
    }

    @Override
    public void onClick(View v) {
        if (dialog != null) {
            dialog.startDownload();
        }
    }
}
