package org.wanshu.reader.core.model;

/** One entry of the source's search results. Optional fields are null when the template lacks them. */
public class BookSummary {
    private final String id;
    private final String title;
    private final String author;
    private final String category;
    private final String latestChapter;
    private final String updated;
    private final String intro;

    public BookSummary(
            String id,
            String title,
            String author,
            String category,
            String latestChapter,
            String updated,
            String intro
    ) {
        this.id = id;
        this.title = title != null ? title : "";
        this.author = author;
        this.category = category;
        this.latestChapter = latestChapter;
        this.updated = updated;
        this.intro = intro;
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

    public String getCategory() {
        return category;
    }

    public String getLatestChapter() {
        return latestChapter;
    }

    public String getUpdated() {
        return updated;
    }

    public String getIntro() {
        return intro;
    }

    @Override
    public String toString() {
        return "BookSummary{" + id + " '" + title + "'}";
    }
}
