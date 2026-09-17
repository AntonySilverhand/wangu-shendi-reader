package org.wanshu.reader.data.content.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;
import org.wanshu.reader.data.content.entity.TocEntryEntity;
import org.wanshu.reader.data.content.entity.TocPageEntity;

@Dao
public interface TocDao {

    @Query("SELECT * FROM toc_entries WHERE book_id = :bookId ORDER BY order_key ASC")
    List<TocEntryEntity> getEntries(String bookId);

    @Query("SELECT * FROM toc_entries WHERE book_id = :bookId AND chapter_id = :chapterId LIMIT 1")
    TocEntryEntity getEntry(String bookId, String chapterId);

    @Query("SELECT COUNT(*) FROM toc_entries WHERE book_id = :bookId")
    int getEntriesCount(String bookId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertEntries(List<TocEntryEntity> entries);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertEntry(TocEntryEntity entry);

    @Query("DELETE FROM toc_entries WHERE book_id = :bookId")
    void deleteEntriesForBook(String bookId);

    @Query("SELECT * FROM toc_pages WHERE book_id = :bookId AND page_index = :pageIndex LIMIT 1")
    TocPageEntity getTocPage(String bookId, int pageIndex);

    @Query("SELECT * FROM toc_pages WHERE book_id = :bookId ORDER BY page_index ASC")
    List<TocPageEntity> getAllTocPages(String bookId);

    @Query("SELECT * FROM toc_pages WHERE book_id = :bookId AND status != 'LOADED' ORDER BY page_index ASC")
    List<TocPageEntity> getPendingTocPages(String bookId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertTocPages(List<TocPageEntity> pages);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertTocPage(TocPageEntity page);

    @Query("UPDATE toc_pages SET status = :status, error = :error, updated_at = :updatedAt WHERE book_id = :bookId AND page_index = :pageIndex")
    void updateTocPage(String bookId, int pageIndex, String status, String error, long updatedAt);

    @Query("SELECT COUNT(*) FROM toc_pages WHERE book_id = :bookId AND status = 'LOADED'")
    int getLoadedPagesCount(String bookId);

    @Query("DELETE FROM toc_pages WHERE book_id = :bookId")
    void deleteTocPagesForBook(String bookId);
}
