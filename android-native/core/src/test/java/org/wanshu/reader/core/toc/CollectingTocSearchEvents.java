package org.wanshu.reader.core.toc;

import java.util.ArrayList;
import java.util.List;

public class CollectingTocSearchEvents implements TocSearchEvents {
    private final List<TocSearchUpdateItem> updates = new ArrayList<TocSearchUpdateItem>();

    @Override
    public synchronized void onUpdate(TocSearchPhase phase, List<TocSearchResult> results) {
        updates.add(new TocSearchUpdateItem(phase, new ArrayList<TocSearchResult>(results)));
    }

    public synchronized List<TocSearchUpdateItem> getUpdates() {
        return new ArrayList<TocSearchUpdateItem>(updates);
    }

    public synchronized TocSearchUpdateItem getLastUpdate() {
        return updates.isEmpty() ? null : updates.get(updates.size() - 1);
    }
}
