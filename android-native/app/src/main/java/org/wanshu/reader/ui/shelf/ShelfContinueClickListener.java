package org.wanshu.reader.ui.shelf;

import android.view.View;
import org.wanshu.reader.core.model.ReadingAnchor;

public class ShelfContinueClickListener implements View.OnClickListener {
    private final ShelfController controller;
    private final String bookId;
    private final ReadingAnchor anchor;

    public ShelfContinueClickListener(ShelfController controller, String bookId, ReadingAnchor anchor) {
        this.controller = controller;
        this.bookId = bookId;
        this.anchor = anchor;
    }

    @Override
    public void onClick(View v) {
        controller.onContinueClicked(bookId, anchor);
    }
}
