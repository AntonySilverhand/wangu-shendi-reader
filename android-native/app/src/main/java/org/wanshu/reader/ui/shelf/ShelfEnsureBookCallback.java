package org.wanshu.reader.ui.shelf;

import org.wanshu.reader.data.repository.CompletionCallback;

public class ShelfEnsureBookCallback implements CompletionCallback {
    private final ShelfController controller;

    public ShelfEnsureBookCallback(ShelfController controller) {
        this.controller = controller;
    }

    @Override
    public void onSuccess() {
        controller.onDefaultBookEnsured();
    }

    @Override
    public void onError(Throwable error) {
        controller.onDefaultBookEnsured();
    }
}
