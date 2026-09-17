package org.wanshu.reader.migration;

public class LegacyMigrationFinishRunnable implements Runnable {
    private final LegacyMigrationActivity activity;
    private final boolean success;
    private final String message;

    public LegacyMigrationFinishRunnable(
            LegacyMigrationActivity activity,
            boolean success,
            String message
    ) {
        this.activity = activity;
        this.success = success;
        this.message = message;
    }

    @Override
    public void run() {
        if (activity != null && !activity.isFinishing()) {
            activity.onMigrationFinished(success, message);
        }
    }
}
