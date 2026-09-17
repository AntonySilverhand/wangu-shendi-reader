package org.wanshu.reader.data.personal.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import org.wanshu.reader.data.personal.entity.SettingsEntity;

@Dao
public interface SettingsDao {

    @Query("SELECT * FROM settings WHERE id = 1 LIMIT 1")
    SettingsEntity getSettings();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void saveSettings(SettingsEntity settings);
}
