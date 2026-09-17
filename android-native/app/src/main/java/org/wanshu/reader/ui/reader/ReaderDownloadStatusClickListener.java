package org.wanshu.reader.ui.reader;

import android.view.View;

public class ReaderDownloadStatusClickListener implements View.OnClickListener {
    private final ReaderController controller;

    public ReaderDownloadStatusClickListener(ReaderController controller) {
        this.controller = controller;
    }

    @Override
    public void onClick(View v) {
        if (controller != null) {
            controller.openDownloads();
        }
    }
}
