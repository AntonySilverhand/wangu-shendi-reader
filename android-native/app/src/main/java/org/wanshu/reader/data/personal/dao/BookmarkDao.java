package org.wanshu.reader.data.personal.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;
import org.wanshu.reader.data.personal.entity.BookmarkEntity;

@Dao
public interface BookmarkDao {

    @Query("SELECT * FROM bookmarks WHERE book_id = :bookId ORDER BY created_at DESC")
    List<BookmarkEntity> getBookmarks(String bookId);

    @Query("SELECT * FROM bookmarks WHERE id = :id LIMIT 1")
    BookmarkEntity getBookmark(String id);

    @Query("SELECT * FROM bookmarks WHERE book_id = :bookId AND chapter_id = :chapterId AND paragraph_index = :paragraph AND ABS(offset_utf16 - :offset) <= 80 ORDER BY ABS(offset_utf16 - :offset) ASC LIMIT 1")
    BookmarkEntity findNearby(String bookId, String chapterId, int paragraph, int offset);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertBookmark(BookmarkEntity bookmark);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<BookmarkEntity> bookmarks);

    @Query("DELETE FROM bookmarks WHERE id = :id")
    int deleteBookmark(String id);

    @Query("DELETE FROM bookmarks WHERE book_id = :bookId")
    int clearBookmarks(String bookId);

    @Query("SELECT * FROM bookmarks ORDER BY created_at DESC")
    List<BookmarkEntity> getAllBookmarks();
}
