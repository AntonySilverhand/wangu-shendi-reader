package org.wanshu.reader.ui.reader;

import android.view.View;

public final class ReaderSettingsClickListener implements View.OnClickListener {
    private final ReaderController controller;
    public ReaderSettingsClickListener(ReaderController controller) { this.controller = controller; }
    @Override public void onClick(View view) { controller.openSettings(); }
}
