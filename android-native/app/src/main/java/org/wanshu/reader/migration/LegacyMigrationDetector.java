package org.wanshu.reader.migration;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.File;
import org.wanshu.reader.data.personal.PersonalDatabase;
import org.wanshu.reader.data.personal.entity.MigrationRunEntity;

public class LegacyMigrationDetector {
    private static final String PREF_NAME = "reader_migration_prefs";
    private static final String KEY_COMPLETED = "legacy_migration_completed";

    public static boolean isMigrationCompleted(Context context) {
        if (context == null) return true;
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return sp.getBoolean(KEY_COMPLETED, false);
    }

    public static void setMigrationCompleted(Context context, boolean completed) {
        if (context == null) return;
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit().putBoolean(KEY_COMPLETED, completed).apply();
    }

    public static boolean shouldPromptMigration(Context context, PersonalDatabase personalDb) {
        if (context == null) return false;

        // 1. SharedPreferences check
        if (isMigrationCompleted(context)) {
            return false;
        }

        // 2. Room database checkpoint check
        if (personalDb != null) {
            try {
                MigrationRunEntity latest = personalDb.migrationRunDao().getLatestRun();
                if (latest != null && "COMPLETE".equals(latest.phase)) {
                    setMigrationCompleted(context, true);
                    return false;
                }
            } catch (Exception ignored) {
            }
        }

        // 3. Inspect application data directory for legacy WebView storage files
        try {
            File dataDir = context.getDataDir();
            File appWebviewDir = new File(dataDir, "app_webview");
            if (!appWebviewDir.exists() || !appWebviewDir.isDirectory()) {
                // Fresh installation: No WebView directory has ever existed on this device
                setMigrationCompleted(context, true);
                return false;
            }

            // Check if any localStorage or IndexedDB directories exist inside app_webview
            File defaultDir = new File(appWebviewDir, "Default");
            File ls1 = new File(appWebviewDir, "Local Storage");
            File ls2 = new File(defaultDir, "Local Storage");
            File idb1 = new File(appWebviewDir, "IndexedDB");
            File idb2 = new File(defaultDir, "IndexedDB");

            boolean hasLegacyStorage = hasFiles(ls1) || hasFiles(ls2) || hasFiles(idb1) || hasFiles(idb2);
            if (!hasLegacyStorage) {
                // Empty WebView cache, mark completed so we never inspect again
                setMigrationCompleted(context, true);
                return false;
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean hasFiles(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return false;
        String[] files = dir.list();
        return files != null && files.length > 0;
    }
}
