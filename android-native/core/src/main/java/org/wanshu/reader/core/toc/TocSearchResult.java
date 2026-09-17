package org.wanshu.reader.core.toc;

import org.wanshu.reader.core.model.TocEntry;

public class TocSearchResult {
    private final int index;
    private final TocEntry entry;

    public TocSearchResult(int index, TocEntry entry) {
        this.index = index;
        this.entry = entry;
    }

    public int getIndex() {
        return index;
    }

    public TocEntry getEntry() {
        return entry;
    }
}
