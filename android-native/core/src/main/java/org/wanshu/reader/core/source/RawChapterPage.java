package org.wanshu.reader.core.source;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RawChapterPage {
    private final String id;
    private final int pageIndex;
    private final String title;
    private final List<String> paragraphs;
    private final String prevChapterId;
    private final String nextChapterId;
    private final Integer nextPageIndex;
    private final List<Integer> sameChapterPages;
    private final Integer declaredPageIndex;

    public RawChapterPage(
            String id,
            int pageIndex,
            String title,
            List<String> paragraphs,
            String prevChapterId,
            String nextChapterId,
            Integer nextPageIndex,
            List<Integer> sameChapterPages,
            Integer declaredPageIndex
    ) {
        this.id = id;
        this.pageIndex = pageIndex;
        this.title = title != null ? title : "";
        this.paragraphs = paragraphs != null
                ? Collections.unmodifiableList(new ArrayList<String>(paragraphs))
                : Collections.<String>emptyList();
        this.prevChapterId = prevChapterId;
        this.nextChapterId = nextChapterId;
        this.nextPageIndex = nextPageIndex;
        this.sameChapterPages = sameChapterPages != null
                ? Collections.unmodifiableList(new ArrayList<Integer>(sameChapterPages))
                : Collections.<Integer>emptyList();
        this.declaredPageIndex = declaredPageIndex;
    }

    public String getId() {
        return id;
    }

    public int getPageIndex() {
        return pageIndex;
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

    public Integer getNextPageIndex() {
        return nextPageIndex;
    }

    public List<Integer> getSameChapterPages() {
        return sameChapterPages;
    }

    public Integer getDeclaredPageIndex() {
        return declaredPageIndex;
    }
}
