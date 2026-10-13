package org.wanshu.reader.ui.reader;

import android.view.View;

public class ReaderBookmarkClickListener implements View.OnClickListener {
    private final ReaderController controller;

    public ReaderBookmarkClickListener(ReaderController controller) {
        this.controller = controller;
    }

    @Override
    public void onClick(View v) {
        if (controller != null) {
            controller.openBookmarks();
        }
    }
}
