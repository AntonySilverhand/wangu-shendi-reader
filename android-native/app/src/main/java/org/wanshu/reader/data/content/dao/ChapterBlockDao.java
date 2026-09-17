package org.wanshu.reader.data.content.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;
import org.wanshu.reader.data.content.entity.ChapterBlockEntity;

@Dao
public interface ChapterBlockDao {

    @Query("SELECT * FROM chapter_blocks WHERE book_id = :bookId AND chapter_id = :chapterId ORDER BY block_index ASC")
    List<ChapterBlockEntity> getBlocks(String bookId, String chapterId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertBlocks(List<ChapterBlockEntity> blocks);

    @Query("DELETE FROM chapter_blocks WHERE book_id = :bookId AND chapter_id = :chapterId")
    void deleteBlocks(String bookId, String chapterId);

    @Query("DELETE FROM chapter_blocks WHERE book_id = :bookId AND chapter_id IN (SELECT chapter_id FROM chapters WHERE book_id = :bookId AND source = 'REMOTE')")
    void clearRemoteBlocks(String bookId);

    @Query("DELETE FROM chapter_blocks WHERE book_id = :bookId")
    void deleteBlocksForBook(String bookId);
}
