package org.wanshu.reader.ui.toc;

import android.view.View;

public class TocLoadAllClickListener implements View.OnClickListener {
    private final TocDialog dialog;

    public TocLoadAllClickListener(TocDialog dialog) {
        this.dialog = dialog;
    }

    @Override
    public void onClick(View v) {
        if (dialog != null) {
            dialog.loadAllPages();
        }
    }
}
