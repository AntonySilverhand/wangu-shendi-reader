package org.wanshu.reader.ui.shelf;

import android.content.DialogInterface;

public class ShelfDeleteConfirmClickListener implements DialogInterface.OnClickListener {
    private final ShelfController controller;
    private final String bookId;

    public ShelfDeleteConfirmClickListener(ShelfController controller, String bookId) {
        this.controller = controller;
        this.bookId = bookId;
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        controller.confirmDeleteBook(bookId);
    }
}
