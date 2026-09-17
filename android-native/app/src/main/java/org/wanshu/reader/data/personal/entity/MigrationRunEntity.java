package org.wanshu.reader.data.personal.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "migration_runs")
public class MigrationRunEntity {
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    public long id = 0L;

    @NonNull
    @ColumnInfo(name = "source")
    public String source = "";

    @NonNull
    @ColumnInfo(name = "phase")
    public String phase = "";

    @NonNull
    @ColumnInfo(name = "checkpoint")
    public String checkpoint = "";

    @NonNull
    @ColumnInfo(name = "summary")
    public String summary = "";

    @NonNull
    @ColumnInfo(name = "error")
    public String error = "";

    @ColumnInfo(name = "created_at")
    public long createdAt = 0L;
}
