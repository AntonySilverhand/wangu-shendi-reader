package org.wanshu.reader.ui.reader;

import android.view.View;

public class ReaderSearchPrevClickListener implements View.OnClickListener {
    private final ReaderController controller;

    public ReaderSearchPrevClickListener(ReaderController controller) {
        this.controller = controller;
    }

    @Override
    public void onClick(View v) {
        controller.prevSearchMatch();
    }
}
