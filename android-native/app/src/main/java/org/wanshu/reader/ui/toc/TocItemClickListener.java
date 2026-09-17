package org.wanshu.reader.ui.toc;

import android.view.View;

public class TocItemClickListener implements View.OnClickListener {
    private final TocDialog dialog;
    private final TocItemModel item;

    public TocItemClickListener(TocDialog dialog, TocItemModel item) {
        this.dialog = dialog;
        this.item = item;
    }

    @Override
    public void onClick(View v) {
        if (dialog != null && item != null) {
            dialog.onItemClicked(item);
        }
    }
}
