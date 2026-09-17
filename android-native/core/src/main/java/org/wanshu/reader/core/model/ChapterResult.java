package org.wanshu.reader.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ChapterResult {
    public static final int CURRENT_SOURCE_SEMANTICS_VERSION = 5;

    private final String bookId;
    private final String chapterId;
    private final String title;
    private final List<String> paragraphs;
    private final String prevChapterId;
    private final String nextChapterId;
    private final int pageCount;
    private final int charCount;
    private final boolean complete;
    private final List<Integer> missingPages;
    private final int sourceSemanticsVersion;

    public ChapterResult(
            String bookId,
            String chapterId,
            String title,
            List<String> paragraphs,
            String prevChapterId,
            String nextChapterId,
            int pageCount,
            int charCount,
            boolean complete,
            List<Integer> missingPages,
            int sourceSemanticsVersion
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
        this.paragraphs = paragraphs != null
                ? Collections.unmodifiableList(new ArrayList<String>(paragraphs))
                : Collections.<String>emptyList();
        this.prevChapterId = prevChapterId != null && !prevChapterId.trim().isEmpty() ? prevChapterId.trim() : null;
        this.nextChapterId = nextChapterId != null && !nextChapterId.trim().isEmpty() ? nextChapterId.trim() : null;
        this.pageCount = pageCount;
        this.charCount = charCount;
        this.complete = complete;
        this.missingPages = missingPages != null
                ? Collections.unmodifiableList(new ArrayList<Integer>(missingPages))
                : Collections.<Integer>emptyList();
        this.sourceSemanticsVersion = sourceSemanticsVersion;
    }

    public ChapterResult(
            String bookId,
            String chapterId,
            String title,
            List<String> paragraphs,
            String prevChapterId,
            String nextChapterId,
            int pageCount,
            int charCount,
            boolean complete,
            List<Integer> missingPages
    ) {
        this(
                bookId,
                chapterId,
                title,
                paragraphs,
                prevChapterId,
                nextChapterId,
                pageCount,
                charCount,
                complete,
                missingPages,
                CURRENT_SOURCE_SEMANTICS_VERSION
        );
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

    public List<String> getParagraphs() {
        return paragraphs;
    }

    public String getPrevChapterId() {
        return prevChapterId;
    }

    public String getNextChapterId() {
        return nextChapterId;
    }

    public int getPageCount() {
        return pageCount;
    }

    public int getCharCount() {
        return charCount;
    }

    public boolean isComplete() {
        return complete;
    }

    public List<Integer> getMissingPages() {
        return missingPages;
    }

    public int getSourceSemanticsVersion() {
        return sourceSemanticsVersion;
    }

    public ChapterKey toChapterKey() {
        return new ChapterKey(bookId, chapterId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChapterResult that = (ChapterResult) o;
        return pageCount == that.pageCount &&
                charCount == that.charCount &&
                complete == that.complete &&
                sourceSemanticsVersion == that.sourceSemanticsVersion &&
                Objects.equals(bookId, that.bookId) &&
                Objects.equals(chapterId, that.chapterId) &&
                Objects.equals(title, that.title) &&
                Objects.equals(paragraphs, that.paragraphs) &&
                Objects.equals(prevChapterId, that.prevChapterId) &&
                Objects.equals(nextChapterId, that.nextChapterId) &&
                Objects.equals(missingPages, that.missingPages);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                bookId,
                chapterId,
                title,
                paragraphs,
                prevChapterId,
                nextChapterId,
                pageCount,
                charCount,
                complete,
                missingPages,
                sourceSemanticsVersion
        );
    }

    @Override
    public String toString() {
        return "ChapterResult{" + bookId + ":" + chapterId + " '" + title + "' complete=" + complete + "}";
    }
}
