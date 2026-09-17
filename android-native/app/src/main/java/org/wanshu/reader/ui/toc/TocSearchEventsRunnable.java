package org.wanshu.reader.ui.toc;

import java.util.List;
import org.wanshu.reader.core.toc.TocSearchPhase;
import org.wanshu.reader.core.toc.TocSearchResult;

public class TocSearchEventsRunnable implements Runnable {
    private final TocDialog dialog;
    private final TocSearchPhase phase;
    private final List<TocSearchResult> results;

    public TocSearchEventsRunnable(
            TocDialog dialog,
            TocSearchPhase phase,
            List<TocSearchResult> results
    ) {
        this.dialog = dialog;
        this.phase = phase;
        this.results = results;
    }

    @Override
    public void run() {
        if (dialog != null) {
            dialog.onSearchUpdated(phase, results);
        }
    }
}
