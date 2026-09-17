package org.wanshu.reader.ui.reader;

import android.view.View;

public class ReaderNextClickListener implements View.OnClickListener {
    private final ReaderController controller;

    public ReaderNextClickListener(ReaderController controller) {
        this.controller = controller;
    }

    @Override
    public void onClick(View v) {
        controller.onNextChapterClicked();
    }
}
