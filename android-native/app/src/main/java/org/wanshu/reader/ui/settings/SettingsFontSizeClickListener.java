package org.wanshu.reader.ui.settings;

import android.view.View;

public class SettingsFontSizeClickListener implements View.OnClickListener {
    private final SettingsDialog dialog;
    private final float delta;

    public SettingsFontSizeClickListener(SettingsDialog dialog, float delta) {
        this.dialog = dialog;
        this.delta = delta;
    }

    @Override
    public void onClick(View v) {
        if (dialog != null) {
            dialog.adjustFontSize(delta);
        }
    }
}
