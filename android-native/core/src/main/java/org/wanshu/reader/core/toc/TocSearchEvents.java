package org.wanshu.reader.core.toc;

import java.util.List;

public interface TocSearchEvents {
    void onUpdate(TocSearchPhase phase, List<TocSearchResult> results);
}
