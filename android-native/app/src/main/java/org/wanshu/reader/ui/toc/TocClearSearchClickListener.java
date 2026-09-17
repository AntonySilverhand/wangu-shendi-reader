package org.wanshu.reader.ui.toc;

import android.view.View;

public class TocClearSearchClickListener implements View.OnClickListener {
    private final TocDialog dialog;

    public TocClearSearchClickListener(TocDialog dialog) {
        this.dialog = dialog;
    }

    @Override
    public void onClick(View v) {
        if (dialog != null) {
            dialog.clearSearch();
        }
    }
}
