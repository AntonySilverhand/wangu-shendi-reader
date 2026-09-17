package org.wanshu.reader.data.content.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "download_policies")
public class DownloadPolicyEntity {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "book_id")
    public String bookId = "";

    @ColumnInfo(name = "auto_cache_enabled")
    public boolean autoCacheEnabled = false;

    @NonNull
    @ColumnInfo(name = "scope")
    public String scope = "ENTIRE_BOOK";

    @ColumnInfo(name = "allow_metered")
    public boolean allowMetered = false;

    @ColumnInfo(name = "max_cache_bytes")
    public long maxCacheBytes = 256L * 1024L * 1024L;
}
