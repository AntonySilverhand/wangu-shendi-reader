package org.wanshu.reader.ui.settings;

import java.util.concurrent.Executor;
import org.wanshu.reader.data.content.StorageStats;
import org.wanshu.reader.data.repository.DataCallback;

public class SettingsStorageStatsCallback implements DataCallback<StorageStats> {
    private final SettingsDialog dialog;
    private final Executor mainExecutor;

    public SettingsStorageStatsCallback(SettingsDialog dialog, Executor mainExecutor) {
        this.dialog = dialog;
        this.mainExecutor = mainExecutor;
    }

    @Override
    public void onSuccess(StorageStats data) {
        if (mainExecutor != null) {
            mainExecutor.execute(new SettingsDisplayStatsRunnable(dialog, data));
        }
    }

    @Override
    public void onError(Throwable error) {
    }
}
