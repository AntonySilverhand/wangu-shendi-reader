package org.wanshu.reader.ui.download;

import android.view.View;

public class DownloadCloseClickListener implements View.OnClickListener {
    private final DownloadsDialog dialog;

    public DownloadCloseClickListener(DownloadsDialog dialog) {
        this.dialog = dialog;
    }

    @Override
    public void onClick(View v) {
        if (dialog != null) {
            dialog.dismiss();
        }
    }
}
