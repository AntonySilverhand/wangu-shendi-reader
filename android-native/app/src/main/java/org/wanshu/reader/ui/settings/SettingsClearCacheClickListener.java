package org.wanshu.reader.ui.settings;

import android.view.View;

public class SettingsClearCacheClickListener implements View.OnClickListener {
    private final SettingsDialog dialog;

    public SettingsClearCacheClickListener(SettingsDialog dialog) {
        this.dialog = dialog;
    }

    @Override
    public void onClick(View v) {
        if (dialog != null) {
            dialog.clearRemoteCache();
        }
    }
}
