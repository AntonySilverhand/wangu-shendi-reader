package org.wanshu.reader.data.content.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "source_cooldown")
public class SourceCooldownEntity {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "source_id")
    public String sourceId = "wanshuge";

    @ColumnInfo(name = "cooldown_until")
    public long cooldownUntil = 0L;

    @NonNull
    @ColumnInfo(name = "reason")
    public String reason = "";
}
