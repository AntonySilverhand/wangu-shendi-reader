package org.wanshu.reader.ui.settings;

public class SettingsExportResultRunnable implements Runnable {
    private final SettingsDialog dialog;
    private final String json;

    public SettingsExportResultRunnable(SettingsDialog dialog, String json) {
        this.dialog = dialog;
        this.json = json;
    }

    @Override
    public void run() {
        if (dialog != null) {
            dialog.onExportResult(json);
        }
    }
}
