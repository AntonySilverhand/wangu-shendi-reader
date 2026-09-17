package org.wanshu.reader.data.personal.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import org.wanshu.reader.data.personal.entity.LastRouteEntity;

@Dao
public interface LastRouteDao {

    @Query("SELECT * FROM last_route WHERE id = 1 LIMIT 1")
    LastRouteEntity getLastRoute();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void saveLastRoute(LastRouteEntity route);
}
