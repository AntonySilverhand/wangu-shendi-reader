package org.wanshu.reader.ui.settings;

import android.widget.CompoundButton;

public class SettingsAutoCacheMeteredChangeListener implements CompoundButton.OnCheckedChangeListener {
    private final SettingsDialog dialog;

    public SettingsAutoCacheMeteredChangeListener(SettingsDialog dialog) {
        this.dialog = dialog;
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (dialog != null) {
            dialog.onAutoCacheMeteredChanged(isChecked);
        }
    }
}
