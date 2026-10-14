package org.wanshu.reader.core.model;

/** Book metadata taken from the og meta tags of a book's detail page (TOC page 1). */
public class BookInfo {
    private final String id;
    private final String title;
    private final String author;
    private final String category;
    private final String status;
    private final String intro;
    private final String latestChapter;
    private final String updatedAt;
    private final String firstChapterId;

    public BookInfo(
            String id,
            String title,
            String author,
            String category,
            String status,
            String intro,
            String latestChapter,
            String updatedAt,
            String firstChapterId
    ) {
        this.id = id;
        this.title = title != null ? title : "";
        this.author = author != null ? author : "";
        this.category = category;
        this.status = status;
        this.intro = intro;
        this.latestChapter = latestChapter;
        this.updatedAt = updatedAt;
        this.firstChapterId = firstChapterId;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    /** May be null. */
    public String getCategory() {
        return category;
    }

    /** May be null. */
    public String getStatus() {
        return status;
    }

    /** May be null when the source only has boilerplate. */
    public String getIntro() {
        return intro;
    }

    /** May be null. */
    public String getLatestChapter() {
        return latestChapter;
    }

    /** May be null. */
    public String getUpdatedAt() {
        return updatedAt;
    }

    /** May be null when the page has no read link. */
    public String getFirstChapterId() {
        return firstChapterId;
    }

    @Override
    public String toString() {
        return "BookInfo{" + id + " '" + title + "' by " + author + "}";
    }
}
