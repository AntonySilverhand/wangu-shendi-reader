package org.wanshu.reader.ui.toc;

import android.view.View;

public class TocItemViewClickListener implements View.OnClickListener {
    private final TocItemClickListener listener;
    private final TocItemModel item;

    public TocItemViewClickListener(TocItemClickListener listener, TocItemModel item) {
        this.listener = listener;
        this.item = item;
    }

    @Override
    public void onClick(View v) {
        if (listener != null && item != null) {
            listener.onTocItemClicked(item);
        }
    }
}
