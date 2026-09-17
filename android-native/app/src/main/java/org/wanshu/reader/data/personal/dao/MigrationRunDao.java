package org.wanshu.reader.data.personal.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;
import org.wanshu.reader.data.personal.entity.MigrationRunEntity;

@Dao
public interface MigrationRunDao {

    @Query("SELECT * FROM migration_runs ORDER BY created_at DESC")
    List<MigrationRunEntity> getAllRuns();

    @Query("SELECT * FROM migration_runs ORDER BY created_at DESC LIMIT 1")
    MigrationRunEntity getLatestRun();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertRun(MigrationRunEntity run);
}
