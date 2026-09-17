package org.wanshu.reader.ui.settings;

import java.util.List;
import java.util.concurrent.Executor;
import org.wanshu.reader.data.personal.PersonalBackupHelper;
import org.wanshu.reader.data.personal.PersonalDatabase;
import org.wanshu.reader.data.personal.entity.BookmarkEntity;
import org.wanshu.reader.data.personal.entity.ReadingHistoryEntity;
import org.wanshu.reader.data.personal.entity.ReadingProgressEntity;
import org.wanshu.reader.data.personal.entity.SettingsEntity;

public class SettingsExportRunnable implements Runnable {
    private final SettingsDialog dialog;
    private final PersonalDatabase db;
    private final Executor mainExecutor;

    public SettingsExportRunnable(SettingsDialog dialog, PersonalDatabase db, Executor mainExecutor) {
        this.dialog = dialog;
        this.db = db;
        this.mainExecutor = mainExecutor;
    }

    @Override
    public void run() {
        String json = null;
        try {
            SettingsEntity settings = db.settingsDao().getSettings();
            List<ReadingProgressEntity> progress = db.readingProgressDao().getAllProgress();
            List<BookmarkEntity> bookmarks = db.bookmarkDao().getAllBookmarks();
            List<ReadingHistoryEntity> history = db.readingHistoryDao().getAllHistory();
            json = PersonalBackupHelper.exportToJson(settings, progress, bookmarks, history);
        } catch (Exception ignored) {
        }
        if (mainExecutor != null) {
            mainExecutor.execute(new SettingsExportResultRunnable(dialog, json));
        }
    }
}
