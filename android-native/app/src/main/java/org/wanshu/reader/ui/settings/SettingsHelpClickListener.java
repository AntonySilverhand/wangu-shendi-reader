package org.wanshu.reader.ui.settings;

import android.view.View;

public class SettingsHelpClickListener implements View.OnClickListener {
    private final SettingsDialog dialog;

    public SettingsHelpClickListener(SettingsDialog dialog) {
        this.dialog = dialog;
    }

    @Override
    public void onClick(View v) {
        if (dialog != null) {
            dialog.showHelp();
        }
    }
}
