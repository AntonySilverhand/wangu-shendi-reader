package org.wanshu.reader.ui.toc;

import android.view.View;

public class TocMainTabClickListener implements View.OnClickListener {
    private final TocDialog dialog;

    public TocMainTabClickListener(TocDialog dialog) {
        this.dialog = dialog;
    }

    @Override
    public void onClick(View v) {
        if (dialog != null) {
            dialog.selectMainTab();
        }
    }
}
