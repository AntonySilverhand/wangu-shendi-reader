package org.wanshu.reader.data.content.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;

@Entity(
    tableName = "chapter_blocks",
    primaryKeys = {"book_id", "chapter_id", "block_index"},
    indices = {
        @Index(value = {"book_id", "chapter_id", "block_index"})
    }
)
public class ChapterBlockEntity {
    @NonNull
    @ColumnInfo(name = "book_id")
    public String bookId = "";

    @NonNull
    @ColumnInfo(name = "chapter_id")
    public String chapterId = "";

    @ColumnInfo(name = "block_index")
    public int blockIndex = 0;

    @ColumnInfo(name = "start_paragraph_index")
    public int startParagraphIndex = 0;

    @ColumnInfo(name = "end_paragraph_index")
    public int endParagraphIndex = 0;

    @NonNull
    @ColumnInfo(name = "content")
    public String content = "";
}
