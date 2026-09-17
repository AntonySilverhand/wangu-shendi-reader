package org.wanshu.reader.data.content.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import org.wanshu.reader.data.content.entity.DownloadPlanEntity;

@Dao
public interface DownloadPlanDao {

    @Query("SELECT * FROM download_plans WHERE book_id = :bookId LIMIT 1")
    DownloadPlanEntity getPlan(String bookId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void savePlan(DownloadPlanEntity plan);

    @Query("DELETE FROM download_plans WHERE book_id = :bookId")
    void deletePlan(String bookId);
}
