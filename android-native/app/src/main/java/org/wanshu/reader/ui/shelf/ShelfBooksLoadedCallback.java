package org.wanshu.reader.ui.shelf;

import java.util.List;
import org.wanshu.reader.data.content.entity.BookEntity;
import org.wanshu.reader.data.repository.DataCallback;

public class ShelfBooksLoadedCallback implements DataCallback<List<BookEntity>> {
    private final ShelfController controller;

    public ShelfBooksLoadedCallback(ShelfController controller) {
        this.controller = controller;
    }

    @Override
    public void onSuccess(List<BookEntity> books) {
        controller.onBooksLoaded(books);
    }

    @Override
    public void onError(Throwable error) {
        controller.onBooksLoadFailed(error);
    }
}
