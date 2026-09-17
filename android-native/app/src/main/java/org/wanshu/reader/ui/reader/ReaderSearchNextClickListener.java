package org.wanshu.reader.ui.reader;

import android.view.View;

public class ReaderSearchNextClickListener implements View.OnClickListener {
    private final ReaderController controller;

    public ReaderSearchNextClickListener(ReaderController controller) {
        this.controller = controller;
    }

    @Override
    public void onClick(View v) {
        controller.nextSearchMatch();
    }
}
