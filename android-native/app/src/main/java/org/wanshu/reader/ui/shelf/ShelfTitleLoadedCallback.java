package org.wanshu.reader.ui.shelf;

import org.wanshu.reader.data.repository.DataCallback;

/** Delivers the title of a book's last-read chapter to the shelf. */
public class ShelfTitleLoadedCallback implements DataCallback<String> {
    private final ShelfController controller;
    private final String bookId;
    private final int generation;

    public ShelfTitleLoadedCallback(ShelfController controller, String bookId, int generation) {
        this.controller = controller;
        this.bookId = bookId;
        this.generation = generation;
    }

    @Override
    public void onSuccess(String title) {
        controller.onChapterTitleLoaded(bookId, title, generation);
    }

    @Override
    public void onError(Throwable error) {
        controller.onChapterTitleLoaded(bookId, "", generation);
    }
}
