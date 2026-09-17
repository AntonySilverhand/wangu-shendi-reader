package org.wanshu.reader.core.toc;

import java.util.List;

public class TocSearchUpdateItem {
    public final TocSearchPhase phase;
    public final List<TocSearchResult> results;

    public TocSearchUpdateItem(TocSearchPhase phase, List<TocSearchResult> results) {
        this.phase = phase;
        this.results = results;
    }
}
