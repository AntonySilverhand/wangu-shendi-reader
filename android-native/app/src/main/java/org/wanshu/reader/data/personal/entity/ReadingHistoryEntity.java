package org.wanshu.reader.data.personal.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "reading_history",
    indices = {
        @Index(value = {"book_id", "read_at"}),
        @Index(value = {"book_id", "chapter_id"})
    }
)
public class ReadingHistoryEntity {
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    public long id = 0L;

    @NonNull
    @ColumnInfo(name = "book_id")
    public String bookId = "";

    @NonNull
    @ColumnInfo(name = "chapter_id")
    public String chapterId = "";

    @NonNull
    @ColumnInfo(name = "title")
    public String title = "";

    @ColumnInfo(name = "read_at")
    public long readAt = 0L;
}
