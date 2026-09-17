package org.wanshu.reader.ui.settings;

public class SettingsClearCacheSuccessRunnable implements Runnable {
    private final SettingsDialog dialog;

    public SettingsClearCacheSuccessRunnable(SettingsDialog dialog) {
        this.dialog = dialog;
    }

    @Override
    public void run() {
        if (dialog != null) {
            dialog.onClearCacheComplete();
        }
    }
}
