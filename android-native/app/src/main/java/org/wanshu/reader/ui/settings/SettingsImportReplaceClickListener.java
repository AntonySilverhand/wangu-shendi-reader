package org.wanshu.reader.ui.settings;

import android.view.View;

public class SettingsImportReplaceClickListener implements View.OnClickListener {
    private final SettingsImportDialog dialog;

    public SettingsImportReplaceClickListener(SettingsImportDialog dialog) {
        this.dialog = dialog;
    }

    @Override
    public void onClick(View v) {
        if (dialog != null) {
            dialog.doImport(true);
        }
    }
}
