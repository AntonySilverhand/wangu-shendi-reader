package org.wanshu.reader.ui.reader;

import android.view.View;

public class ReaderTocClickListener implements View.OnClickListener {
    private final ReaderController controller;

    public ReaderTocClickListener(ReaderController controller) {
        this.controller = controller;
    }

    @Override
    public void onClick(View v) {
        if (controller != null) {
            controller.openToc();
        }
    }
}
