package org.wanshu.reader.data.content.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;

@Entity(
    tableName = "toc_pages",
    primaryKeys = {"book_id", "page_index"}
)
public class TocPageEntity {
    @NonNull
    @ColumnInfo(name = "book_id")
    public String bookId = "";

    @ColumnInfo(name = "page_index")
    public int pageIndex = 1;

    @NonNull
    @ColumnInfo(name = "status")
    public String status = "PENDING";

    @NonNull
    @ColumnInfo(name = "error")
    public String error = "";

    @ColumnInfo(name = "updated_at")
    public long updatedAt = 0L;
}
