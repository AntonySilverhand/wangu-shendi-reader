package org.wanshu.reader.ui.settings;

import org.wanshu.reader.data.personal.entity.SettingsEntity;
import org.wanshu.reader.data.repository.DataCallback;

public class SettingsLoadedCallback implements DataCallback<SettingsEntity> {
    private final SettingsDialog dialog;

    public SettingsLoadedCallback(SettingsDialog dialog) {
        this.dialog = dialog;
    }

    @Override
    public void onSuccess(SettingsEntity result) {
        if (dialog != null) {
            dialog.onSettingsLoaded(result);
        }
    }

    @Override
    public void onError(Throwable error) {
        if (dialog != null) {
            dialog.onSettingsError(error != null ? error.getMessage() : "读取设置失败");
        }
    }
}
