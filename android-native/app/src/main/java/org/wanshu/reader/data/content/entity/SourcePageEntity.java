package org.wanshu.reader.data.content.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;

@Entity(
    tableName = "source_pages",
    primaryKeys = {"book_id", "chapter_id", "page_index"},
    indices = {
        @Index(value = {"book_id", "chapter_id"})
    }
)
public class SourcePageEntity {
    @NonNull
    @ColumnInfo(name = "book_id")
    public String bookId = "";

    @NonNull
    @ColumnInfo(name = "chapter_id")
    public String chapterId = "";

    @ColumnInfo(name = "page_index")
    public int pageIndex = 0;

    @ColumnInfo(name = "declared_page_index")
    public Integer declaredPageIndex = null;

    @NonNull
    @ColumnInfo(name = "paragraphs_json")
    public String paragraphsJson = "[]";

    @NonNull
    @ColumnInfo(name = "discovered_links_json")
    public String discoveredLinksJson = "[]";

    @ColumnInfo(name = "has_terminal_evidence")
    public boolean hasTerminalEvidence = false;

    @ColumnInfo(name = "fetched_at")
    public long fetchedAt = 0L;
}
