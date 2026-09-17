package org.wanshu.reader.ui.settings;

import android.view.View;

public class SettingsAutoCacheScopeClickListener implements View.OnClickListener {
    private final SettingsDialog dialog;
    private final String scope;

    public SettingsAutoCacheScopeClickListener(SettingsDialog dialog, String scope) {
        this.dialog = dialog;
        this.scope = scope;
    }

    @Override
    public void onClick(View v) {
        if (dialog != null) {
            dialog.onAutoCacheScopeSelected(scope);
        }
    }
}
