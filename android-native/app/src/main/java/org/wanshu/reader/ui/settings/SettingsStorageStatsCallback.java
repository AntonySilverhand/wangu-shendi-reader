package org.wanshu.reader.ui.settings;

import org.wanshu.reader.data.content.StorageStats;
import org.wanshu.reader.data.repository.DataCallback;

public class SettingsStorageStatsCallback implements DataCallback<StorageStats> {
    private final SettingsDialog dialog;

    public SettingsStorageStatsCallback(SettingsDialog dialog) {
        this.dialog = dialog;
    }

    @Override
    public void onSuccess(StorageStats result) {
        if (dialog != null) {
            dialog.onStorageStatsLoaded(result);
        }
    }

    @Override
    public void onError(Throwable error) {
        if (dialog != null) {
            dialog.onStorageStatsError(error != null ? error.getMessage() : "读取缓存信息失败");
        }
    }
}
