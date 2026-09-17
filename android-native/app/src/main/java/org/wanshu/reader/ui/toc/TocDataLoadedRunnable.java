package org.wanshu.reader.ui.toc;

import java.util.List;

public class TocDataLoadedRunnable implements Runnable {
    private final TocDialog dialog;
    private final List<TocItemModel> items;

    public TocDataLoadedRunnable(TocDialog dialog, List<TocItemModel> items) {
        this.dialog = dialog;
        this.items = items;
    }

    @Override
    public void run() {
        if (dialog != null) {
            dialog.onDataLoaded(items);
        }
    }
}
