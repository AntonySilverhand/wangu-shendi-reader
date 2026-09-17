package org.wanshu.reader.ui.settings;

import android.widget.CompoundButton;

public class SettingsPrefetchChangeListener implements CompoundButton.OnCheckedChangeListener {
    private final SettingsDialog dialog;

    public SettingsPrefetchChangeListener(SettingsDialog dialog) {
        this.dialog = dialog;
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (dialog != null) {
            dialog.onPrefetchChanged(isChecked);
        }
    }
}
