package org.wanshu.reader.data.content.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "books")
public class BookEntity {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "book_id")
    public String bookId = "";

    @NonNull
    @ColumnInfo(name = "source_type")
    public String sourceType = "ONLINE";

    @NonNull
    @ColumnInfo(name = "title")
    public String title = "";

    @NonNull
    @ColumnInfo(name = "author")
    public String author = "";

    @NonNull
    @ColumnInfo(name = "import_status")
    public String importStatus = "READY";

    @ColumnInfo(name = "chapter_count")
    public int chapterCount = 0;

    @ColumnInfo(name = "total_bytes")
    public long totalBytes = 0L;

    @ColumnInfo(name = "created_at")
    public long createdAt = 0L;

    @ColumnInfo(name = "updated_at")
    public long updatedAt = 0L;
}
