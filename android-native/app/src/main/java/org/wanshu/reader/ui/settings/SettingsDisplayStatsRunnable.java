package org.wanshu.reader.ui.settings;

import org.wanshu.reader.data.content.StorageStats;

public class SettingsDisplayStatsRunnable implements Runnable {
    private final SettingsDialog dialog;
    private final StorageStats stats;

    public SettingsDisplayStatsRunnable(SettingsDialog dialog, StorageStats stats) {
        this.dialog = dialog;
        this.stats = stats;
    }

    @Override
    public void run() {
        if (dialog != null) {
            dialog.onStorageStatsLoaded(stats);
        }
    }
}
