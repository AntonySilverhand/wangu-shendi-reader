package org.wanshu.reader.migration;

import android.view.View;

public class LegacyMigrationFinishClickListener implements View.OnClickListener {
    private final LegacyMigrationActivity activity;

    public LegacyMigrationFinishClickListener(LegacyMigrationActivity activity) {
        this.activity = activity;
    }

    @Override
    public void onClick(View v) {
        if (activity != null) {
            activity.finish();
        }
    }
}
