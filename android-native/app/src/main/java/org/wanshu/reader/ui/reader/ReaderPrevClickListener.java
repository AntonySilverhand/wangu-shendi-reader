package org.wanshu.reader.ui.reader;

import android.view.View;

public class ReaderPrevClickListener implements View.OnClickListener {
    private final ReaderController controller;

    public ReaderPrevClickListener(ReaderController controller) {
        this.controller = controller;
    }

    @Override
    public void onClick(View v) {
        controller.onPrevChapterClicked();
    }
}
