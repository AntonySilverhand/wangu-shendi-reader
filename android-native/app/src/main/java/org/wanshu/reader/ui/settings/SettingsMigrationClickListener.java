package org.wanshu.reader.ui.settings;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import org.wanshu.reader.migration.LegacyMigrationActivity;

public class SettingsMigrationClickListener implements View.OnClickListener {
    private final Context context;
    private final Dialog dialog;

    public SettingsMigrationClickListener(Context context, Dialog dialog) {
        this.context = context;
        this.dialog = dialog;
    }

    @Override
    public void onClick(View v) {
        if (dialog != null) {
            dialog.dismiss();
        }
        if (context != null) {
            Intent intent = new Intent(context, LegacyMigrationActivity.class);
            intent.putExtra("manual_trigger", true);
            context.startActivity(intent);
        }
    }
}
