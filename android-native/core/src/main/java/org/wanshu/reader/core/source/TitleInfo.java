package org.wanshu.reader.core.source;

public class TitleInfo {
    private final String displayTitle;
    private final Integer number;
    private final boolean extra;

    public TitleInfo(String displayTitle, Integer number, boolean extra) {
        this.displayTitle = displayTitle != null ? displayTitle : "";
        this.number = number;
        this.extra = extra;
    }

    public String getDisplayTitle() {
        return displayTitle;
    }

    public Integer getNumber() {
        return number;
    }

    public boolean isExtra() {
        return extra;
    }
}
