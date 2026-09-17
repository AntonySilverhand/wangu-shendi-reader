package org.wanshu.reader.data.content.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;

@Entity(
    tableName = "chapters",
    primaryKeys = {"book_id", "chapter_id"},
    indices = {
        @Index(value = {"book_id", "is_complete"})
    }
)
public class ChapterEntity {
    @NonNull
    @ColumnInfo(name = "book_id")
    public String bookId = "";

    @NonNull
    @ColumnInfo(name = "chapter_id")
    public String chapterId = "";

    @NonNull
    @ColumnInfo(name = "title")
    public String title = "";

    @NonNull
    @ColumnInfo(name = "source")
    public String source = "";

    @ColumnInfo(name = "is_complete")
    public boolean isComplete = false;

    @ColumnInfo(name = "source_semantics_version")
    public int sourceSemanticsVersion = 5;

    @ColumnInfo(name = "char_count")
    public int charCount = 0;

    @ColumnInfo(name = "bytes")
    public long bytes = 0L;

    @ColumnInfo(name = "prev_chapter_id")
    public String prevChapterId = null;

    @ColumnInfo(name = "next_chapter_id")
    public String nextChapterId = null;

    @ColumnInfo(name = "revision")
    public long revision = 0L;

    @NonNull
    @ColumnInfo(name = "missing_pages_json")
    public String missingPagesJson = "[]";

    @ColumnInfo(name = "fetched_at")
    public long fetchedAt = 0L;
}
