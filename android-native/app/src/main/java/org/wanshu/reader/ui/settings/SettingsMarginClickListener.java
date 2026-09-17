package org.wanshu.reader.ui.settings;

import android.view.View;

public class SettingsMarginClickListener implements View.OnClickListener {
    private final SettingsDialog dialog;
    private final int delta;

    public SettingsMarginClickListener(SettingsDialog dialog, int delta) {
        this.dialog = dialog;
        this.delta = delta;
    }

    @Override
    public void onClick(View v) {
        if (dialog != null) {
            dialog.adjustMargin(delta);
        }
    }
}
