package org.wanshu.reader.ui.shelf;

import android.view.View;

public class ShelfStartClickListener implements View.OnClickListener {
    private final ShelfController controller;
    private final String bookId;

    public ShelfStartClickListener(ShelfController controller, String bookId) {
        this.controller = controller;
        this.bookId = bookId;
    }

    @Override
    public void onClick(View v) {
        controller.onStartClicked(bookId);
    }
}
