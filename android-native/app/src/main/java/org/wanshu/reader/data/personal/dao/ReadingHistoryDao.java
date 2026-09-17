package org.wanshu.reader.data.personal.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;
import org.wanshu.reader.data.personal.entity.ReadingHistoryEntity;

@Dao
public interface ReadingHistoryDao {

    @Query("SELECT * FROM reading_history WHERE book_id = :bookId ORDER BY read_at DESC LIMIT 30")
    List<ReadingHistoryEntity> getHistoryForBook(String bookId);

    @Query("SELECT * FROM reading_history ORDER BY read_at DESC LIMIT :limit")
    List<ReadingHistoryEntity> getRecentHistory(int limit);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertHistory(ReadingHistoryEntity entry);

    @Query("DELETE FROM reading_history WHERE book_id = :bookId AND chapter_id = :chapterId")
    void deleteEntry(String bookId, String chapterId);

    @Query("DELETE FROM reading_history WHERE book_id = :bookId AND id NOT IN (SELECT id FROM reading_history WHERE book_id = :bookId ORDER BY read_at DESC LIMIT 30)")
    void trimHistory(String bookId);

    @Query("DELETE FROM reading_history WHERE book_id = :bookId")
    void clearHistoryForBook(String bookId);

    @Query("SELECT * FROM reading_history ORDER BY read_at DESC")
    List<ReadingHistoryEntity> getAllHistory();
}
