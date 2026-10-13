package org.wanshu.reader.ui.settings;

import org.wanshu.reader.data.repository.CompletionCallback;

public class SettingsSaveCompletedCallback implements CompletionCallback {
    private final SettingsDialog dialog;

    public SettingsSaveCompletedCallback(SettingsDialog dialog) {
        this.dialog = dialog;
    }

    @Override
    public void onSuccess() {
        if (dialog != null) {
            dialog.onSettingsSaved();
        }
    }

    @Override
    public void onError(Throwable error) {
        if (dialog != null) {
            dialog.onSettingsError(error != null ? error.getMessage() : "保存设置失败");
        }
    }
}
