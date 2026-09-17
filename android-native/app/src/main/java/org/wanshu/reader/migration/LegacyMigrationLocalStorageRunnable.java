package org.wanshu.reader.migration;

import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;

public class LegacyMigrationLocalStorageRunnable implements Runnable {
    private final LegacyMigrationActivity activity;
    private final LegacyMigrationEngine engine;
    private final WebView webView;
    private final String settingsJson;
    private final String personalJson;
    private final String localBooksJson;

    public LegacyMigrationLocalStorageRunnable(
            LegacyMigrationActivity activity,
            LegacyMigrationEngine engine,
            WebView webView,
            String settingsJson,
            String personalJson,
            String localBooksJson
    ) {
        this.activity = activity;
        this.engine = engine;
        this.webView = webView;
        this.settingsJson = settingsJson;
        this.personalJson = personalJson;
        this.localBooksJson = localBooksJson;
    }

    @Override
    public void run() {
        boolean success = false;
        try {
            if (engine != null) {
                engine.importLocalStorage(settingsJson, personalJson, localBooksJson);
                success = true;
            }
        } catch (Exception ignored) {
            success = false;
        }

        Handler mainHandler = new Handler(Looper.getMainLooper());
        if (activity != null) {
            mainHandler.post(new LegacyMigrationStatusRunnable(
                    activity,
                    "已迁移阅读设置与书签",
                    "正在读取已缓存章节与目录...",
                    10,
                    100
            ));
        }

        String ackJs = "if (window.onLocalStorageAck) { window.onLocalStorageAck(" + success + "); }";
        mainHandler.post(new LegacyMigrationAckRunnable(webView, ackJs));
    }
}
