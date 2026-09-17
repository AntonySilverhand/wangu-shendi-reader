package org.wanshu.reader.ui.settings;

import java.util.concurrent.Executor;
import org.wanshu.reader.data.repository.CompletionCallback;

public class SettingsClearCacheCallback implements CompletionCallback {
    private final SettingsDialog dialog;
    private final Executor mainExecutor;

    public SettingsClearCacheCallback(SettingsDialog dialog, Executor mainExecutor) {
        this.dialog = dialog;
        this.mainExecutor = mainExecutor;
    }

    @Override
    public void onSuccess() {
        if (mainExecutor != null) {
            mainExecutor.execute(new SettingsClearCacheSuccessRunnable(dialog));
        }
    }

    @Override
    public void onError(Throwable error) {
    }
}
