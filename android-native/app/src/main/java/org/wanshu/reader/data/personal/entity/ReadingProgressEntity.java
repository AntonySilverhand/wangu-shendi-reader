package org.wanshu.reader.data.personal.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "reading_progress")
public class ReadingProgressEntity {
    @PrimaryKey
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

    @ColumnInfo(name = "updated_at")
    public long updatedAt = 0L;

    @ColumnInfo(name = "sequence_number")
    public long sequenceNumber = 0L;

    @NonNull
    @ColumnInfo(name = "paragraph_hash")
    public String paragraphHash = "";

    @NonNull
    @ColumnInfo(name = "quote")
    public String quote = "";

    @ColumnInfo(name = "content_revision")
    public long contentRevision = 0L;
}
