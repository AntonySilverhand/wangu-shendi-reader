package org.wanshu.reader.data.content.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;
import org.wanshu.reader.data.content.entity.ChapterEntity;

@Dao
public interface ChapterDao {

    @Query("SELECT * FROM chapters WHERE book_id = :bookId AND chapter_id = :chapterId LIMIT 1")
    ChapterEntity getChapter(String bookId, String chapterId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertChapter(ChapterEntity chapter);

    @Query("SELECT COUNT(*) FROM chapters WHERE book_id = :bookId AND is_complete = 1")
    int getCompletedCount(String bookId);

    @Query("SELECT COUNT(*) FROM chapters WHERE book_id = :bookId AND is_complete = 0")
    int getPartialCount(String bookId);

    @Query("SELECT COALESCE(SUM(bytes), 0) FROM chapters WHERE book_id = :bookId")
    long getTotalBytes(String bookId);

    @Query("SELECT chapter_id FROM chapters WHERE book_id = :bookId AND is_complete = 1")
    List<String> getCompletedChapterIds(String bookId);

    @Query("SELECT chapter_id FROM chapters WHERE book_id = :bookId")
    List<String> getAllCachedChapterIds(String bookId);

    @Query("DELETE FROM chapters WHERE book_id = :bookId AND source = 'REMOTE'")
    int clearRemoteCache(String bookId);

    @Query("DELETE FROM chapters WHERE book_id = :bookId")
    int deleteChaptersForBook(String bookId);
}
