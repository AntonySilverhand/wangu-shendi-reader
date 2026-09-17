package org.wanshu.reader.ui.settings;

import android.view.View;

public class SettingsLineHeightClickListener implements View.OnClickListener {
    private final SettingsDialog dialog;
    private final float delta;

    public SettingsLineHeightClickListener(SettingsDialog dialog, float delta) {
        this.dialog = dialog;
        this.delta = delta;
    }

    @Override
    public void onClick(View v) {
        if (dialog != null) {
            dialog.adjustLineHeight(delta);
        }
    }
}
