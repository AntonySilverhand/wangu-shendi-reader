package org.wanshu.reader.core.source;

public class TocItemRaw {
    private final String id;
    private final String title;

    public TocItemRaw(String id, String title) {
        this.id = id != null ? id : "";
        this.title = title != null ? title : "";
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    @Override
    public String toString() {
        return "TocItemRaw{" + id + ": " + title + "}";
    }
}
