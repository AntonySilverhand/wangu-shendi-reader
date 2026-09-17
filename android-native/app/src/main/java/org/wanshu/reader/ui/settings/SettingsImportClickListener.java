package org.wanshu.reader.ui.settings;

import android.view.View;

public class SettingsImportClickListener implements View.OnClickListener {
    private final SettingsDialog dialog;

    public SettingsImportClickListener(SettingsDialog dialog) {
        this.dialog = dialog;
    }

    @Override
    public void onClick(View v) {
        if (dialog != null) {
            dialog.importPersonalData();
        }
    }
}
