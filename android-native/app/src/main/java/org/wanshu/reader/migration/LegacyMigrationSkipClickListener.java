package org.wanshu.reader.migration;

import android.view.View;

public class LegacyMigrationSkipClickListener implements View.OnClickListener {
    private final LegacyMigrationActivity activity;

    public LegacyMigrationSkipClickListener(LegacyMigrationActivity activity) {
        this.activity = activity;
    }

    @Override
    public void onClick(View v) {
        if (activity != null) {
            activity.onSkipClicked();
        }
    }
}
