package org.wanshu.reader.data.personal.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;
import org.wanshu.reader.data.personal.entity.ReadingProgressEntity;

@Dao
public interface ReadingProgressDao {

    @Query("SELECT * FROM reading_progress WHERE book_id = :bookId LIMIT 1")
    ReadingProgressEntity getProgress(String bookId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void saveProgress(ReadingProgressEntity progress);

    @Query("DELETE FROM reading_progress WHERE book_id = :bookId")
    void deleteProgress(String bookId);

    @Query("SELECT * FROM reading_progress")
    List<ReadingProgressEntity> getAllProgress();
}
