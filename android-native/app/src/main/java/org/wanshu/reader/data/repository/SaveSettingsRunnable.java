package org.wanshu.reader.data.repository;

import org.wanshu.reader.data.personal.PersonalDatabase;
import org.wanshu.reader.data.personal.entity.SettingsEntity;

public class SaveSettingsRunnable implements Runnable {
    private final PersonalDatabase db;
    private final SettingsEntity settings;
    private final CompletionCallback callback;

    public SaveSettingsRunnable(
            PersonalDatabase db,
            SettingsEntity settings,
            CompletionCallback callback
    ) {
        this.db = db;
        this.settings = settings;
        this.callback = callback;
    }

    @Override
    public void run() {
        try {
            if (settings != null) {
                settings.id = 1;
                db.settingsDao().saveSettings(settings);
            }
            if (callback != null) {
                callback.onSuccess();
            }
        } catch (Throwable t) {
            if (callback != null) {
                callback.onError(t);
            }
        }
    }
}
