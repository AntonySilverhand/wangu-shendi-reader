package org.wanshu.reader.ui.shelf;

import org.wanshu.reader.data.repository.CompletionCallback;

/** "Start reading" from a search result: once the book is on the shelf, open its first chapter. */
public class ShelfStartAfterAddCallback implements CompletionCallback {
    private final ShelfController controller;
    private final String bookId;
    private final String firstChapterId;

    public ShelfStartAfterAddCallback(ShelfController controller, String bookId, String firstChapterId) {
        this.controller = controller;
        this.bookId = bookId;
        this.firstChapterId = firstChapterId;
    }

    @Override
    public void onSuccess() {
        controller.startOnlineBook(bookId, firstChapterId);
    }

    @Override
    public void onError(Throwable error) {
        controller.onStartFailed(error);
    }
}
