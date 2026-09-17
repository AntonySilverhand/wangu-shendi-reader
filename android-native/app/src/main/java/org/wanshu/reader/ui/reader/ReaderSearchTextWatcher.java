package org.wanshu.reader.ui.reader;

import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;

public class ReaderSearchTextWatcher implements TextWatcher {
    private final ReaderController controller;
    private final Handler handler;
    private ReaderSearchQueryDebounceRunnable pendingRunnable;

    public ReaderSearchTextWatcher(ReaderController controller) {
        this.controller = controller;
        this.handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (pendingRunnable != null) {
            handler.removeCallbacks(pendingRunnable);
        }
        String query = s != null ? s.toString() : "";
        pendingRunnable = new ReaderSearchQueryDebounceRunnable(controller, query);
        handler.postDelayed(pendingRunnable, 120L);
    }

    @Override
    public void afterTextChanged(Editable s) {
    }
}
