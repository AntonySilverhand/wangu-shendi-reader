package org.wanshu.reader.core.model;

import java.util.Objects;

public class TocEntry {
    private final String bookId;
    private final String chapterId;
    private final String title;
    private final long orderKey;
    private final boolean isExtra;
    private final int sourcePageIndex;

    public TocEntry(
            String bookId,
            String chapterId,
            String title,
            long orderKey,
            boolean isExtra,
            int sourcePageIndex
    ) {
        if (bookId == null || bookId.trim().isEmpty()) {
            throw new IllegalArgumentException("bookId must not be empty");
        }
        if (chapterId == null || chapterId.trim().isEmpty()) {
            throw new IllegalArgumentException("chapterId must not be empty");
        }
        this.bookId = bookId.trim();
        this.chapterId = chapterId.trim();
        this.title = title != null ? title.trim() : "";
        this.orderKey = orderKey;
        this.isExtra = isExtra;
        this.sourcePageIndex = sourcePageIndex;
    }

    public String getBookId() {
        return bookId;
    }

    public String getChapterId() {
        return chapterId;
    }

    public String getTitle() {
        return title;
    }

    public long getOrderKey() {
        return orderKey;
    }

    public boolean isExtra() {
        return isExtra;
    }

    public int getSourcePageIndex() {
        return sourcePageIndex;
    }

    public ChapterKey toChapterKey() {
        return new ChapterKey(bookId, chapterId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TocEntry tocEntry = (TocEntry) o;
        return orderKey == tocEntry.orderKey &&
                isExtra == tocEntry.isExtra &&
                sourcePageIndex == tocEntry.sourcePageIndex &&
                Objects.equals(bookId, tocEntry.bookId) &&
                Objects.equals(chapterId, tocEntry.chapterId) &&
                Objects.equals(title, tocEntry.title);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bookId, chapterId, title, orderKey, isExtra, sourcePageIndex);
    }

    @Override
    public String toString() {
        return "TocEntry{" + bookId + ":" + chapterId + " '" + title + "'}";
    }
}
