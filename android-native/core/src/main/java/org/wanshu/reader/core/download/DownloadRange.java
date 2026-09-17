package org.wanshu.reader.core.download;

public enum DownloadRange {
    NEXT_50(50, "后续 50 章"),
    NEXT_100(100, "后续 100 章"),
    NEXT_200(200, "后续 200 章"),
    NEXT_300(300, "后续 300 章"),
    ENTIRE_BOOK(-1, "全书 (含番外)");

    private final int count;
    private final String label;

    DownloadRange(int count, String label) {
        this.count = count;
        this.label = label;
    }

    public int getCount() {
        return count;
    }

    public String getLabel() {
        return label;
    }
}
