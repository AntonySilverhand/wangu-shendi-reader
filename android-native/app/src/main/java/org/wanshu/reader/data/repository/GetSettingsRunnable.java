package org.wanshu.reader.data.repository;

import org.wanshu.reader.data.personal.PersonalDatabase;
import org.wanshu.reader.data.personal.entity.SettingsEntity;

public class GetSettingsRunnable implements Runnable {
    private final PersonalDatabase db;
    private final DataCallback<SettingsEntity> callback;

    public GetSettingsRunnable(PersonalDatabase db, DataCallback<SettingsEntity> callback) {
        this.db = db;
        this.callback = callback;
    }

    @Override
    public void run() {
        try {
            SettingsEntity settings = db.settingsDao().getSettings();
            if (settings == null) {
                settings = new SettingsEntity();
                db.settingsDao().saveSettings(settings);
            }
            if (callback != null) {
                callback.onSuccess(settings);
            }
        } catch (Throwable t) {
            if (callback != null) {
                callback.onError(t);
            }
        }
    }
}
