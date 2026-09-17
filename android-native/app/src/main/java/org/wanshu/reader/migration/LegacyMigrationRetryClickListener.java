package org.wanshu.reader.migration;

import android.view.View;

public class LegacyMigrationRetryClickListener implements View.OnClickListener {
    private final LegacyMigrationActivity activity;

    public LegacyMigrationRetryClickListener(LegacyMigrationActivity activity) {
        this.activity = activity;
    }

    @Override
    public void onClick(View v) {
        if (activity != null) {
            activity.onRetryClicked();
        }
    }
}
