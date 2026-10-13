package org.wanshu.reader.ui.toc;

import android.text.Editable;
import android.text.TextWatcher;

public class TocSearchTextWatcher implements TextWatcher {
    private final TocDialog dialog;

    public TocSearchTextWatcher(TocDialog dialog) {
        this.dialog = dialog;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    @Override
    public void afterTextChanged(Editable s) {
        if (dialog != null) {
            dialog.onSearchQueryChanged(s != null ? s.toString() : "");
        }
    }
}
