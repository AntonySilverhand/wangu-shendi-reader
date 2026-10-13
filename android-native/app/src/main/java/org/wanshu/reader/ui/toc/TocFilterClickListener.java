package org.wanshu.reader.ui.toc;

import android.view.View;

public class TocFilterClickListener implements View.OnClickListener {
    private final TocDialog dialog;
    private final TocFilterType filter;

    public TocFilterClickListener(TocDialog dialog, TocFilterType filter) {
        this.dialog = dialog;
        this.filter = filter;
    }

    @Override
    public void onClick(View v) {
        if (dialog != null) {
            dialog.onFilterClicked(filter);
        }
    }
}
