package org.wanshu.reader.ui.shelf;

import android.view.View;

public class ShelfDeleteClickListener implements View.OnClickListener {
    private final ShelfController controller;
    private final String bookId;
    private final String bookTitle;

    public ShelfDeleteClickListener(ShelfController controller, String bookId, String bookTitle) {
        this.controller = controller;
        this.bookId = bookId;
        this.bookTitle = bookTitle;
    }

    @Override
    public void onClick(View v) {
        controller.onDeleteClicked(bookId, bookTitle);
    }
}
