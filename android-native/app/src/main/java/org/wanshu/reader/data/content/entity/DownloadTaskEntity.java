package org.wanshu.reader.data.content.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;

@Entity(
    tableName = "download_tasks",
    primaryKeys = {"book_id", "chapter_id"},
    indices = {
        @Index(value = {"book_id", "state", "priority", "next_attempt_at"})
    }
)
public class DownloadTaskEntity {
    @NonNull
    @ColumnInfo(name = "book_id")
    public String bookId = "";

    @NonNull
    @ColumnInfo(name = "chapter_id")
    public String chapterId = "";

    @ColumnInfo(name = "demand_flags")
    public int demandFlags = 0;

    @NonNull
    @ColumnInfo(name = "state")
    public String state = "PENDING";

    @ColumnInfo(name = "priority")
    public int priority = 0;

    @ColumnInfo(name = "attempts")
    public int attempts = 0;

    @ColumnInfo(name = "next_attempt_at")
    public long nextAttemptAt = 0L;

    @NonNull
    @ColumnInfo(name = "error_type")
    public String errorType = "";

    @ColumnInfo(name = "run_token")
    public long runToken = 0L;
}
