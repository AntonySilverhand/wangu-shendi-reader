package org.wanshu.reader.ui.settings;

import android.view.View;

public class SettingsThemeClickListener implements View.OnClickListener {
    private final SettingsDialog dialog;
    private final String theme;

    public SettingsThemeClickListener(SettingsDialog dialog, String theme) {
        this.dialog = dialog;
        this.theme = theme;
    }

    @Override
    public void onClick(View v) {
        if (dialog != null && theme != null) {
            dialog.onThemeSelected(theme);
        }
    }
}
