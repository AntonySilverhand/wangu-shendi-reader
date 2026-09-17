package org.wanshu.reader.data.personal;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import org.wanshu.reader.data.personal.dao.BookmarkDao;
import org.wanshu.reader.data.personal.dao.LastRouteDao;
import org.wanshu.reader.data.personal.dao.MigrationRunDao;
import org.wanshu.reader.data.personal.dao.ReadingHistoryDao;
import org.wanshu.reader.data.personal.dao.ReadingProgressDao;
import org.wanshu.reader.data.personal.dao.SettingsDao;
import org.wanshu.reader.data.personal.entity.BookmarkEntity;
import org.wanshu.reader.data.personal.entity.LastRouteEntity;
import org.wanshu.reader.data.personal.entity.MigrationRunEntity;
import org.wanshu.reader.data.personal.entity.ReadingHistoryEntity;
import org.wanshu.reader.data.personal.entity.ReadingProgressEntity;
import org.wanshu.reader.data.personal.entity.SettingsEntity;

@Database(
    entities = {
        ReadingProgressEntity.class,
        BookmarkEntity.class,
        ReadingHistoryEntity.class,
        SettingsEntity.class,
        LastRouteEntity.class,
        MigrationRunEntity.class
    },
    version = 1,
    exportSchema = true
)
public abstract class PersonalDatabase extends RoomDatabase {
    public abstract ReadingProgressDao readingProgressDao();
    public abstract BookmarkDao bookmarkDao();
    public abstract ReadingHistoryDao readingHistoryDao();
    public abstract SettingsDao settingsDao();
    public abstract LastRouteDao lastRouteDao();
    public abstract MigrationRunDao migrationRunDao();
}
