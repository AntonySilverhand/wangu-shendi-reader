package org.wanshu.reader.ui.settings;

import org.wanshu.reader.data.repository.CompletionCallback;

public class SettingsClearCacheCallback implements CompletionCallback {
    private final SettingsDialog dialog;

    public SettingsClearCacheCallback(SettingsDialog dialog) {
        this.dialog = dialog;
    }

    @Override
    public void onSuccess() {
        if (dialog != null) {
            dialog.onCacheCleared();
        }
    }

    @Override
    public void onError(Throwable error) {
        if (dialog != null) {
            dialog.onClearCacheError(error != null ? error.getMessage() : "清理缓存失败");
        }
    }
}
