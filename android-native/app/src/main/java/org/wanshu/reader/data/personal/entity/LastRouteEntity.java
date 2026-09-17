package org.wanshu.reader.data.personal.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "last_route")
public class LastRouteEntity {
    @PrimaryKey
    @ColumnInfo(name = "id")
    public int id = 1;

    @NonNull
    @ColumnInfo(name = "route_type")
    public String routeType = "SHELF";

    @NonNull
    @ColumnInfo(name = "book_id")
    public String bookId = "";

    @NonNull
    @ColumnInfo(name = "chapter_id")
    public String chapterId = "";

    @ColumnInfo(name = "updated_at")
    public long updatedAt = 0L;
}
