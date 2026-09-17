package org.wanshu.reader.ui.reader;

import org.wanshu.reader.data.personal.entity.SettingsEntity;
import org.wanshu.reader.data.repository.DataCallback;

public class ReaderSettingsLoadedCallback implements DataCallback<SettingsEntity> {
    private final ReaderController controller;

    public ReaderSettingsLoadedCallback(ReaderController controller) {
        this.controller = controller;
    }

    @Override
    public void onSuccess(SettingsEntity settings) {
        controller.applySettings(settings);
    }

    @Override
    public void onError(Throwable error) {
        // Keep default settings
    }
}
