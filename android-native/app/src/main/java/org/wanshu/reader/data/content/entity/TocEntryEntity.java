package org.wanshu.reader.data.content.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;

@Entity(
    tableName = "toc_entries",
    primaryKeys = {"book_id", "chapter_id"},
    indices = {
        @Index(value = {"book_id", "order_key"})
    }
)
public class TocEntryEntity {
    @NonNull
    @ColumnInfo(name = "book_id")
    public String bookId = "";

    @NonNull
    @ColumnInfo(name = "chapter_id")
    public String chapterId = "";

    @NonNull
    @ColumnInfo(name = "title")
    public String title = "";

    @ColumnInfo(name = "order_key")
    public long orderKey = 0L;

    @ColumnInfo(name = "is_extra")
    public boolean isExtra = false;

    @ColumnInfo(name = "source_page_index")
    public int sourcePageIndex = 1;
}
