package org.wanshu.reader.ui.reader;

import android.view.View;

public class ReaderSearchToggleClickListener implements View.OnClickListener {
    private final ReaderController controller;

    public ReaderSearchToggleClickListener(ReaderController controller) {
        this.controller = controller;
    }

    @Override
    public void onClick(View v) {
        controller.toggleSearch();
    }
}
