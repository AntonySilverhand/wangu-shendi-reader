package org.wanshu.reader.migration;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

public class LegacyMigrationCompleteRunnable implements Runnable {
    private final LegacyMigrationActivity activity;
    private final LegacyMigrationEngine engine;
    private final Context context;
    private final String runId;
    private final String summaryJson;

    public LegacyMigrationCompleteRunnable(
            LegacyMigrationActivity activity,
            LegacyMigrationEngine engine,
            Context context,
            String runId,
            String summaryJson
    ) {
        this.activity = activity;
        this.engine = engine;
        this.context = context;
        this.runId = runId;
        this.summaryJson = summaryJson;
    }

    @Override
    public void run() {
        try {
            if (engine != null) {
                engine.completeMigration(runId, summaryJson);
            }
            if (context != null) {
                LegacyMigrationDetector.setMigrationCompleted(context, true);
            }
        } catch (Exception ignored) {
        }

        Handler mainHandler = new Handler(Looper.getMainLooper());
        if (activity != null) {
            mainHandler.post(new LegacyMigrationStatusRunnable(
                    activity,
                    "数据迁移完成",
                    "所有阅读进度、书签和缓存已导入",
                    100,
                    100
            ));
            mainHandler.post(new LegacyMigrationFinishRunnable(activity, true, "迁移成功"));
        }
    }
}
