package org.wanshu.reader.data.content.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;
import org.wanshu.reader.data.content.entity.BookEntity;

@Dao
public interface BookDao {

    @Query("SELECT * FROM books ORDER BY updated_at DESC")
    List<BookEntity> getAllBooks();

    @Query("SELECT * FROM books WHERE book_id = :bookId LIMIT 1")
    BookEntity getBook(String bookId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertBook(BookEntity book);

    @Update
    void updateBook(BookEntity book);

    @Query("UPDATE books SET import_status = :status, chapter_count = :chapterCount, total_bytes = :totalBytes, updated_at = :updatedAt WHERE book_id = :bookId")
    void updateImportStatus(String bookId, String status, int chapterCount, long totalBytes, long updatedAt);

    @Query("DELETE FROM books WHERE book_id = :bookId AND source_type = 'LOCAL_TXT'")
    int deleteLocalBook(String bookId);

    @Query("DELETE FROM books WHERE book_id = :bookId")
    int deleteBook(String bookId);
}
