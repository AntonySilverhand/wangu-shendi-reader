package org.wanshu.reader.ui.reader;

import org.wanshu.reader.data.personal.entity.SettingsEntity;

public final class ReaderSettingsUiRunnable implements Runnable {
    private final ReaderController controller;
    private final SettingsEntity settings;
    public ReaderSettingsUiRunnable(ReaderController controller, SettingsEntity settings) { this.controller = controller; this.settings = settings; }
    @Override public void run() { controller.applySettingsOnUi(settings); }
}
