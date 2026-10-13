package org.wanshu.reader.ui.toc;

public class TocItemModel {
    private final String chapterId;
    private final String title;
    private final boolean isExtra;
    private final long orderKey;
    private final boolean isCurrent;
    private final TocCacheStatus cacheStatus;

    public TocItemModel(
            String chapterId,
            String title,
            boolean isExtra,
            long orderKey,
            boolean isCurrent,
            TocCacheStatus cacheStatus
    ) {
        this.chapterId = chapterId != null ? chapterId : "";
        this.title = title != null ? title : "";
        this.isExtra = isExtra;
        this.orderKey = orderKey;
        this.isCurrent = isCurrent;
        this.cacheStatus = cacheStatus != null ? cacheStatus : TocCacheStatus.UNCACHED;
    }

    public String getChapterId() {
        return chapterId;
    }

    public String getTitle() {
        return title;
    }

    public boolean isExtra() {
        return isExtra;
    }

    public long getOrderKey() {
        return orderKey;
    }

    public boolean isCurrent() {
        return isCurrent;
    }

    public TocCacheStatus getCacheStatus() {
        return cacheStatus;
    }
}
