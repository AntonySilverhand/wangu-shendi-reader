package org.wanshu.reader.ui.settings;

import java.util.concurrent.Executor;
import org.wanshu.reader.data.personal.PersonalBackupHelper;
import org.wanshu.reader.data.personal.PersonalDatabase;
import org.wanshu.reader.data.personal.entity.SettingsEntity;

public class SettingsImportRunnable implements Runnable {
    private final SettingsDialog dialog;
    private final PersonalDatabase db;
    private final String json;
    private final boolean replace;
    private final Executor mainExecutor;

    public SettingsImportRunnable(
            SettingsDialog dialog,
            PersonalDatabase db,
            String json,
            boolean replace,
            Executor mainExecutor
    ) {
        this.dialog = dialog;
        this.db = db;
        this.json = json;
        this.replace = replace;
        this.mainExecutor = mainExecutor;
    }

    @Override
    public void run() {
        boolean success = PersonalBackupHelper.importFromJson(json, db, replace);
        SettingsEntity updatedSettings = null;
        if (success) {
            try {
                updatedSettings = db.settingsDao().getSettings();
            } catch (Exception ignored) {
            }
        }
        if (mainExecutor != null) {
            mainExecutor.execute(new SettingsImportResultRunnable(dialog, success, updatedSettings));
        }
    }
}
