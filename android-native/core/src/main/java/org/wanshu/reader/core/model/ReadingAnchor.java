package org.wanshu.reader.core.model;

import java.util.Objects;

public class ReadingAnchor {
    private final String bookId;
    private final String chapterId;
    private final int paragraphIndex;
    private final int offsetUtf16;
    private final long updatedAt;
    private final String paragraphHash;
    private final String quote;
    private final long contentRevision;

    public ReadingAnchor(
            String bookId,
            String chapterId,
            int paragraphIndex,
            int offsetUtf16,
            long updatedAt,
            String paragraphHash,
            String quote,
            long contentRevision
    ) {
        if (bookId == null || bookId.trim().isEmpty()) {
            throw new IllegalArgumentException("bookId must not be empty");
        }
        if (chapterId == null || chapterId.trim().isEmpty()) {
            throw new IllegalArgumentException("chapterId must not be empty");
        }
        if (paragraphIndex < 0) {
            throw new IllegalArgumentException("paragraphIndex must be >= 0");
        }
        if (offsetUtf16 < 0) {
            throw new IllegalArgumentException("offsetUtf16 must be >= 0");
        }
        this.bookId = bookId.trim();
        this.chapterId = chapterId.trim();
        this.paragraphIndex = paragraphIndex;
        this.offsetUtf16 = offsetUtf16;
        this.updatedAt = updatedAt;
        this.paragraphHash = paragraphHash != null ? paragraphHash : "";
        this.quote = quote != null ? quote : "";
        this.contentRevision = contentRevision;
    }

    public ReadingAnchor(
            String bookId,
            String chapterId,
            int paragraphIndex,
            int offsetUtf16,
            long updatedAt
    ) {
        this(bookId, chapterId, paragraphIndex, offsetUtf16, updatedAt, "", "", 0L);
    }

    public String getBookId() {
        return bookId;
    }

    public String getChapterId() {
        return chapterId;
    }

    public int getParagraphIndex() {
        return paragraphIndex;
    }

    public int getOffsetUtf16() {
        return offsetUtf16;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public String getParagraphHash() {
        return paragraphHash;
    }

    public String getQuote() {
        return quote;
    }

    public long getContentRevision() {
        return contentRevision;
    }

    public ChapterKey toChapterKey() {
        return new ChapterKey(bookId, chapterId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReadingAnchor that = (ReadingAnchor) o;
        return paragraphIndex == that.paragraphIndex &&
                offsetUtf16 == that.offsetUtf16 &&
                Objects.equals(bookId, that.bookId) &&
                Objects.equals(chapterId, that.chapterId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bookId, chapterId, paragraphIndex, offsetUtf16);
    }

    @Override
    public String toString() {
        return bookId + ":" + chapterId + "#p" + paragraphIndex + "+" + offsetUtf16;
    }
}
