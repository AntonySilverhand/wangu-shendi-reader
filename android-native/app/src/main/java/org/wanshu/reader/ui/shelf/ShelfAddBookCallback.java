package org.wanshu.reader.ui.shelf;

import org.wanshu.reader.data.repository.CompletionCallback;

/** After a book was added from search: refresh the shelf list, then tell the dialog. */
public class ShelfAddBookCallback implements CompletionCallback {
    private final ShelfController controller;
    private final CompletionCallback next;

    public ShelfAddBookCallback(ShelfController controller, CompletionCallback next) {
        this.controller = controller;
        this.next = next;
    }

    @Override
    public void onSuccess() {
        controller.onDefaultBookEnsured();
        if (next != null) next.onSuccess();
    }

    @Override
    public void onError(Throwable error) {
        if (next != null) next.onError(error);
    }
}
