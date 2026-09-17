package org.wanshu.reader.ui.settings;

import org.wanshu.reader.data.personal.entity.SettingsEntity;

public class SettingsImportResultRunnable implements Runnable {
    private final SettingsDialog dialog;
    private final boolean success;
    private final SettingsEntity settings;

    public SettingsImportResultRunnable(SettingsDialog dialog, boolean success, SettingsEntity settings) {
        this.dialog = dialog;
        this.success = success;
        this.settings = settings;
    }

    @Override
    public void run() {
        if (dialog != null) {
            dialog.onImportResult(success, settings);
        }
    }
}
