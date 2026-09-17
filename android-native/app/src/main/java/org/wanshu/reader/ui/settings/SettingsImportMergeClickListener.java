package org.wanshu.reader.ui.settings;

import android.view.View;

public class SettingsImportMergeClickListener implements View.OnClickListener {
    private final SettingsImportDialog dialog;

    public SettingsImportMergeClickListener(SettingsImportDialog dialog) {
        this.dialog = dialog;
    }

    @Override
    public void onClick(View v) {
        if (dialog != null) {
            dialog.doImport(false);
        }
    }
}
