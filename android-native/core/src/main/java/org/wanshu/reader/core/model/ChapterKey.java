package org.wanshu.reader.core.model;

import java.util.Objects;

public class ChapterKey {
    private final String bookId;
    private final String chapterId;

    public ChapterKey(String bookId, String chapterId) {
        if (bookId == null || bookId.trim().isEmpty()) {
            throw new IllegalArgumentException("bookId must not be empty");
        }
        if (chapterId == null || chapterId.trim().isEmpty()) {
            throw new IllegalArgumentException("chapterId must not be empty");
        }
        this.bookId = bookId.trim();
        this.chapterId = chapterId.trim();
    }

    public String getBookId() {
        return bookId;
    }

    public String getChapterId() {
        return chapterId;
    }

    public boolean isOnlineBook() {
        return "36780".equals(bookId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChapterKey that = (ChapterKey) o;
        return Objects.equals(bookId, that.bookId) && Objects.equals(chapterId, that.chapterId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bookId, chapterId);
    }

    @Override
    public String toString() {
        return bookId + ":" + chapterId;
    }
}
