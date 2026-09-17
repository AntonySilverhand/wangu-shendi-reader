package org.wanshu.reader.ui.toc;

public class TocItemModel {
    private final String chapterId;
    private final String title;
    private final long orderKey;
    private final boolean isExtra;
    private final boolean isCurrent;
    private final boolean isComplete;
    private final boolean isPartial;

    public TocItemModel(
            String chapterId,
            String title,
            long orderKey,
            boolean isExtra,
            boolean isCurrent,
            boolean isComplete,
            boolean isPartial
    ) {
        this.chapterId = chapterId != null ? chapterId : "";
        this.title = title != null ? title : "";
        this.orderKey = orderKey;
        this.isExtra = isExtra;
        this.isCurrent = isCurrent;
        this.isComplete = isComplete;
        this.isPartial = isPartial;
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

    public boolean isCurrent() {
        return isCurrent;
    }

    public boolean isComplete() {
        return isComplete;
    }

    public boolean isPartial() {
        return isPartial;
    }
}
