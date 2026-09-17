package org.wanshu.reader.ui.reader;

import android.view.View;

public class ReaderRetryClickListener implements View.OnClickListener {
    private final ReaderController controller;

    public ReaderRetryClickListener(ReaderController controller) {
        this.controller = controller;
    }

    @Override
    public void onClick(View v) {
        controller.onRetryClicked();
    }
}
