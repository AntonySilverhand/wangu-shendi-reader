package org.wanshu.reader.ui.settings;

import android.view.View;

public class SettingsImportCancelClickListener implements View.OnClickListener {
    private final SettingsImportDialog dialog;

    public SettingsImportCancelClickListener(SettingsImportDialog dialog) {
        this.dialog = dialog;
    }

    @Override
    public void onClick(View v) {
        if (dialog != null) {
            dialog.dismiss();
        }
    }
}
