package org.wanshu.reader.ui.toc;

import java.util.List;
import org.wanshu.reader.core.toc.TocSearchEvents;
import org.wanshu.reader.core.toc.TocSearchPhase;
import org.wanshu.reader.core.toc.TocSearchResult;

public class TocSearchLoadedEvents implements TocSearchEvents {
    private final TocDialog dialog;

    public TocSearchLoadedEvents(TocDialog dialog) {
        this.dialog = dialog;
    }

    @Override
    public void onUpdate(TocSearchPhase phase, List<TocSearchResult> results) {
        if (dialog != null) {
            dialog.onSearchResultsUpdated();
        }
    }
}
