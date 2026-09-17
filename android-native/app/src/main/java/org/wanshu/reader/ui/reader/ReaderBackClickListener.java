package org.wanshu.reader.ui.reader;

import android.view.View;

public class ReaderBackClickListener implements View.OnClickListener {
    private final ReaderController controller;

    public ReaderBackClickListener(ReaderController controller) {
        this.controller = controller;
    }

    @Override
    public void onClick(View v) {
        controller.onBackToShelfClicked();
    }
}
