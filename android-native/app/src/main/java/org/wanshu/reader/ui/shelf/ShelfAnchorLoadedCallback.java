package org.wanshu.reader.ui.shelf;

import org.wanshu.reader.core.model.ReadingAnchor;
import org.wanshu.reader.data.repository.DataCallback;

public class ShelfAnchorLoadedCallback implements DataCallback<ReadingAnchor> {
    private final ShelfController controller;
    private final String bookId;

    public ShelfAnchorLoadedCallback(ShelfController controller, String bookId) {
        this.controller = controller;
        this.bookId = bookId;
    }

    @Override
    public void onSuccess(ReadingAnchor anchor) {
        controller.onAnchorLoaded(bookId, anchor);
    }

    @Override
    public void onError(Throwable error) {
        controller.onAnchorLoaded(bookId, null);
    }
}
