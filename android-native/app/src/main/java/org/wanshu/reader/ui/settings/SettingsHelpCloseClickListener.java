package org.wanshu.reader.ui.settings;

import android.view.View;

public class SettingsHelpCloseClickListener implements View.OnClickListener {
    private final SettingsHelpDialog dialog;

    public SettingsHelpCloseClickListener(SettingsHelpDialog dialog) {
        this.dialog = dialog;
    }

    @Override
    public void onClick(View v) {
        if (dialog != null) {
            dialog.dismiss();
        }
    }
}
