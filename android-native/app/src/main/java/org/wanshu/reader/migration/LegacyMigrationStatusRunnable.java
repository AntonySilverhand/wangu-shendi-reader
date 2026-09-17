package org.wanshu.reader.migration;

public class LegacyMigrationStatusRunnable implements Runnable {
    private final LegacyMigrationActivity activity;
    private final String statusText;
    private final String detailText;
    private final int progress;
    private final int max;

    public LegacyMigrationStatusRunnable(
            LegacyMigrationActivity activity,
            String statusText,
            String detailText,
            int progress,
            int max
    ) {
        this.activity = activity;
        this.statusText = statusText;
        this.detailText = detailText;
        this.progress = progress;
        this.max = max;
    }

    @Override
    public void run() {
        if (activity != null && !activity.isFinishing()) {
            activity.updateStatus(statusText, detailText, progress, max);
        }
    }
}
