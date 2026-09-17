package org.wanshu.reader.migration;

import android.os.Handler;
import android.os.Looper;

public class LegacyMigrationErrorRunnable implements Runnable {
    private final LegacyMigrationActivity activity;
    private final LegacyMigrationEngine engine;
    private final String runId;
    private final String errorStage;
    private final String errorMessage;

    public LegacyMigrationErrorRunnable(
            LegacyMigrationActivity activity,
            LegacyMigrationEngine engine,
            String runId,
            String errorStage,
            String errorMessage
    ) {
        this.activity = activity;
        this.engine = engine;
        this.runId = runId;
        this.errorStage = errorStage;
        this.errorMessage = errorMessage;
    }

    @Override
    public void run() {
        try {
            if (engine != null) {
                engine.recordFailure(runId, errorStage, errorMessage);
            }
        } catch (Exception ignored) {
        }

        Handler mainHandler = new Handler(Looper.getMainLooper());
        if (activity != null) {
            mainHandler.post(new LegacyMigrationStatusRunnable(
                    activity,
                    "迁移过程中出现问题",
                    "错误阶段: " + errorStage + " (" + errorMessage + ")",
                    0,
                    100
            ));
            mainHandler.post(new LegacyMigrationFinishRunnable(activity, false, errorMessage));
        }
    }
}
