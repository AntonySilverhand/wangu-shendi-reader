package org.wanshu.reader.core.queue;

import java.util.Objects;

public class RequestKey {
    private final String value;

    public RequestKey(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("RequestKey cannot be null or empty");
        }
        this.value = value;
    }

    public static RequestKey chapterPage(String bookId, String chapterId, int pageIndex) {
        return new RequestKey("page:" + bookId + ":" + chapterId + ":" + pageIndex);
    }

    public static RequestKey chapterAll(String bookId, String chapterId) {
        return new RequestKey("chapter:" + bookId + ":" + chapterId);
    }

    public static RequestKey tocPage(String bookId, int sourcePageIndex) {
        return new RequestKey("toc:" + bookId + ":" + sourcePageIndex);
    }

    public static RequestKey of(String customKey) {
        return new RequestKey(customKey);
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RequestKey that = (RequestKey) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
