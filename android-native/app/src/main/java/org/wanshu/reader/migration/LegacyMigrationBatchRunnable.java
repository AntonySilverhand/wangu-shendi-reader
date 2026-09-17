package org.wanshu.reader.migration;

import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;

public class LegacyMigrationBatchRunnable implements Runnable {
    private final LegacyMigrationActivity activity;
    private final LegacyMigrationEngine engine;
    private final WebView webView;
    private final String runId;
    private final String storeName;
    private final String batchJson;
    private final int batchIndex;
    private final boolean isLastBatch;
    private final String checksum;

    public LegacyMigrationBatchRunnable(
            LegacyMigrationActivity activity,
            LegacyMigrationEngine engine,
            WebView webView,
            String runId,
            String storeName,
            String batchJson,
            int batchIndex,
            boolean isLastBatch,
            String checksum
    ) {
        this.activity = activity;
        this.engine = engine;
        this.webView = webView;
        this.runId = runId;
        this.storeName = storeName;
        this.batchJson = batchJson;
        this.batchIndex = batchIndex;
        this.isLastBatch = isLastBatch;
        this.checksum = checksum;
    }

    @Override
    public void run() {
        int itemsImported = 0;
        try {
            if ("chapters".equalsIgnoreCase(storeName)) {
                itemsImported = engine.importChaptersBatch(batchJson);
            } else if ("toc".equalsIgnoreCase(storeName)) {
                itemsImported = engine.importTocBatch(batchJson);
            }
        } catch (Exception e) {
            // Error importing batch
        }

        Handler mainHandler = new Handler(Looper.getMainLooper());
        if (activity != null) {
            String status = "chapters".equalsIgnoreCase(storeName)
                    ? "正在迁移章节 (第 " + (batchIndex + 1) + " 批)..."
                    : "正在迁移目录结构...";
            String detail = "已处理 " + itemsImported + " 条记录";
            int progress = Math.min(10 + (batchIndex + 1) * 2, 90);
            mainHandler.post(new LegacyMigrationStatusRunnable(
                    activity,
                    status,
                    detail,
                    progress,
                    100
            ));
        }

        String ackJs = "if (window.onBatchAck) { window.onBatchAck('" + storeName + "', " + batchIndex + ", null, " + isLastBatch + "); }";
        mainHandler.post(new LegacyMigrationAckRunnable(webView, ackJs));
    }
}
