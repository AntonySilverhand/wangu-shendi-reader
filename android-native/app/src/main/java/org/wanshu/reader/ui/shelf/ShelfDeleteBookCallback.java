package org.wanshu.reader.ui.shelf;

import org.wanshu.reader.data.repository.CompletionCallback;

public class ShelfDeleteBookCallback implements CompletionCallback {
    private final ShelfController controller;

    public ShelfDeleteBookCallback(ShelfController controller) {
        this.controller = controller;
    }

    @Override
    public void onSuccess() {
        controller.onBookDeleted();
    }

    @Override
    public void onError(Throwable error) {
        controller.onBookDeleteFailed(error);
    }
}
