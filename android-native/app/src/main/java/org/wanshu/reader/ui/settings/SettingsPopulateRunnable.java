package org.wanshu.reader.ui.settings;

import org.wanshu.reader.data.personal.entity.SettingsEntity;

public class SettingsPopulateRunnable implements Runnable {
    private final SettingsDialog dialog;
    private final SettingsEntity settings;

    public SettingsPopulateRunnable(SettingsDialog dialog, SettingsEntity settings) {
        this.dialog = dialog;
        this.settings = settings;
    }

    @Override
    public void run() {
        if (dialog != null) {
            dialog.onSettingsLoaded(settings);
        }
    }
}
