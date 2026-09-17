package org.wanshu.reader.ui.settings;

import org.wanshu.reader.data.personal.entity.SettingsEntity;

public interface SettingsChangeListener {
    void onSettingsChanged(SettingsEntity settings);
}
