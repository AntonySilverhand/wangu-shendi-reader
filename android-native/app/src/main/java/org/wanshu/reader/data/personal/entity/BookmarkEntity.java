package org.wanshu.reader.data.personal.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "bookmarks",
    indices = {
        @Index(value = {"book_id", "created_at"}),
        @Index(value = {"book_id", "chapter_id"})
    }
)
public class BookmarkEntity {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id = "";

    @NonNull
    @ColumnInfo(name = "book_id")
    public String bookId = "";

    @NonNull
    @ColumnInfo(name = "chapter_id")
    public String chapterId = "";

    @ColumnInfo(name = "paragraph_index")
    public int paragraphIndex = 0;

    @ColumnInfo(name = "offset_utf16")
    public int offsetUtf16 = 0;

    @NonNull
    @ColumnInfo(name = "snippet")
    public String snippet = "";

    @ColumnInfo(name = "created_at")
    public long createdAt = 0L;
}
