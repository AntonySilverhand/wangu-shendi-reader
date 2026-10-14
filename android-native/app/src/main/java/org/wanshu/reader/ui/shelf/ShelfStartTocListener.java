package org.wanshu.reader.ui.shelf;

import org.wanshu.reader.data.repository.TocLoadListener;

/** Waits for the first TOC page of a book that has no saved TOC yet, then lets the shelf open its first chapter. */
public class ShelfStartTocListener implements TocLoadListener {
    private final ShelfController controller;
    private final String bookId;

    public ShelfStartTocListener(ShelfController controller, String bookId) {
        this.controller = controller;
        this.bookId = bookId;
    }

    @Override
    public void onProgress(int loaded, int total, boolean finished, String error) {
        if (finished) {
            controller.onStartTocFinished(bookId, error);
        }
    }
}
