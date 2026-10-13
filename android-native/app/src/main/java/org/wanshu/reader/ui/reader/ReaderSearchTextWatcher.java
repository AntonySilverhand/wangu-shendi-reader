package org.wanshu.reader.ui.reader;

import android.text.Editable;
import android.text.TextWatcher;

public class ReaderSearchTextWatcher implements TextWatcher {
    private final ReaderController controller;

    public ReaderSearchTextWatcher(ReaderController controller) {
        this.controller = controller;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    @Override
    public void afterTextChanged(Editable s) {
        controller.onSearchQueryChanged(s != null ? s.toString() : "");
    }
}
