package org.wanshu.reader.core.source;

import java.util.Objects;

public class ChapterLinkResult {
    private final String id;
    private final int pageIndex;

    public ChapterLinkResult(String id, int pageIndex) {
        this.id = id;
        this.pageIndex = pageIndex;
    }

    public String getId() {
        return id;
    }

    public int getPageIndex() {
        return pageIndex;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChapterLinkResult that = (ChapterLinkResult) o;
        return pageIndex == that.pageIndex && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, pageIndex);
    }

    @Override
    public String toString() {
        return id + "_" + pageIndex;
    }
}
