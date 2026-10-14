package org.wanshu.reader.ui.shelf;

import org.wanshu.reader.core.model.ReadingAnchor;
import org.wanshu.reader.data.repository.DataCallback;

public class ShelfAnchorLoadedCallback implements DataCallback<ReadingAnchor> {
    private final ShelfController controller;
    private final String bookId;
    private final int generation;

    public ShelfAnchorLoadedCallback(ShelfController controller, String bookId, int generation) {
        this.controller = controller;
        this.bookId = bookId;
        this.generation = generation;
    }

    @Override
    public void onSuccess(ReadingAnchor anchor) {
        controller.onAnchorLoaded(bookId, anchor, generation);
    }

    @Override
    public void onError(Throwable error) {
        controller.onAnchorLoaded(bookId, null, generation);
    }
}
