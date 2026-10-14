package org.wanshu.reader.ui.shelf;

import java.util.List;
import org.wanshu.reader.data.content.entity.TocEntryEntity;
import org.wanshu.reader.data.repository.DataCallback;

/** Receives the saved TOC of an online book when the user asked to read it from the start. */
public class ShelfFirstEntryCallback implements DataCallback<List<TocEntryEntity>> {
    private final ShelfController controller;
    private final String bookId;
    private final boolean afterTocLoad;

    public ShelfFirstEntryCallback(ShelfController controller, String bookId, boolean afterTocLoad) {
        this.controller = controller;
        this.bookId = bookId;
        this.afterTocLoad = afterTocLoad;
    }

    @Override
    public void onSuccess(List<TocEntryEntity> entries) {
        controller.onFirstEntriesLoaded(bookId, entries, afterTocLoad);
    }

    @Override
    public void onError(Throwable error) {
        controller.onStartFailed(error);
    }
}
