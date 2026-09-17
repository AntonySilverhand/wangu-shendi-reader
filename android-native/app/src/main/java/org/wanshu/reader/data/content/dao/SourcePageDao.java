package org.wanshu.reader.data.content.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;
import org.wanshu.reader.data.content.entity.SourcePageEntity;

@Dao
public interface SourcePageDao {

    @Query("SELECT * FROM source_pages WHERE book_id = :bookId AND chapter_id = :chapterId ORDER BY page_index ASC")
    List<SourcePageEntity> getPages(String bookId, String chapterId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertPage(SourcePageEntity page);

    @Query("DELETE FROM source_pages WHERE book_id = :bookId AND chapter_id = :chapterId")
    void deletePagesForChapter(String bookId, String chapterId);

    @Query("DELETE FROM source_pages WHERE book_id = :bookId")
    void deletePagesForBook(String bookId);
}
