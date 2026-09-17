package org.wanshu.reader.ui.reader;

import android.view.View;

public class ReaderSearchCloseClickListener implements View.OnClickListener {
    private final ReaderController controller;

    public ReaderSearchCloseClickListener(ReaderController controller) {
        this.controller = controller;
    }

    @Override
    public void onClick(View v) {
        controller.closeSearch();
    }
}
