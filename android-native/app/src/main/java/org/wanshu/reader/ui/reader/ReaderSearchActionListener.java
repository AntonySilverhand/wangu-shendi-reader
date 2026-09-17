package org.wanshu.reader.ui.reader;

import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

public class ReaderSearchActionListener implements TextView.OnEditorActionListener {
    private final ReaderController controller;

    public ReaderSearchActionListener(ReaderController controller) {
        this.controller = controller;
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_SEARCH
                || actionId == EditorInfo.IME_ACTION_DONE
                || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
            if (event != null && event.isShiftPressed()) {
                controller.prevSearchMatch();
            } else {
                controller.nextSearchMatch();
            }
            return true;
        }
        return false;
    }
}
