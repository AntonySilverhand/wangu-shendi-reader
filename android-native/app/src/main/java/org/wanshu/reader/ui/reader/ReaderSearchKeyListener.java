package org.wanshu.reader.ui.reader;

import android.view.KeyEvent;
import android.view.View;

public class ReaderSearchKeyListener implements View.OnKeyListener {
    private final ReaderController controller;

    public ReaderSearchKeyListener(ReaderController controller) {
        this.controller = controller;
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.isShiftPressed()) {
                controller.prevSearchMatch();
            } else {
                controller.nextSearchMatch();
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_ESCAPE && event.getAction() == KeyEvent.ACTION_DOWN) {
            controller.closeSearch();
            return true;
        }
        return false;
    }
}
