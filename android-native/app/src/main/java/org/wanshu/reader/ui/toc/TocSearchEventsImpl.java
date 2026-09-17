package org.wanshu.reader.ui.toc;

import java.util.List;
import java.util.concurrent.Executor;
import org.wanshu.reader.core.toc.TocSearchEvents;
import org.wanshu.reader.core.toc.TocSearchPhase;
import org.wanshu.reader.core.toc.TocSearchResult;

public class TocSearchEventsImpl implements TocSearchEvents {
    private final TocDialog dialog;
    private final Executor mainExecutor;

    public TocSearchEventsImpl(TocDialog dialog, Executor mainExecutor) {
        this.dialog = dialog;
        this.mainExecutor = mainExecutor;
    }

    @Override
    public void onUpdate(TocSearchPhase phase, List<TocSearchResult> results) {
        if (mainExecutor != null) {
            mainExecutor.execute(new TocSearchEventsRunnable(dialog, phase, results));
        }
    }
}
