package org.wanshu.reader.ui.settings;

import java.util.concurrent.Executor;
import org.wanshu.reader.data.personal.entity.SettingsEntity;
import org.wanshu.reader.data.repository.DataCallback;

public class SettingsLoadedCallback implements DataCallback<SettingsEntity> {
    private final SettingsDialog dialog;
    private final Executor mainExecutor;

    public SettingsLoadedCallback(SettingsDialog dialog, Executor mainExecutor) {
        this.dialog = dialog;
        this.mainExecutor = mainExecutor;
    }

    @Override
    public void onSuccess(SettingsEntity data) {
        if (mainExecutor != null) {
            mainExecutor.execute(new SettingsPopulateRunnable(dialog, data));
        }
    }

    @Override
    public void onError(Throwable error) {
    }
}
