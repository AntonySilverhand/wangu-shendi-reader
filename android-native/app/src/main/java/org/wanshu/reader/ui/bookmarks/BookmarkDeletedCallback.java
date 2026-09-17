package org.wanshu.reader.ui.bookmarks;

import java.util.concurrent.Executor;
import org.wanshu.reader.data.repository.CompletionCallback;

public class BookmarkDeletedCallback implements CompletionCallback {
    private final BookmarksDialog dialog;
    private final String bookmarkId;
    private final Executor mainExecutor;

    public BookmarkDeletedCallback(BookmarksDialog dialog, String bookmarkId, Executor mainExecutor) {
        this.dialog = dialog;
        this.bookmarkId = bookmarkId;
        this.mainExecutor = mainExecutor;
    }

    @Override
    public void onSuccess() {
        if (mainExecutor != null) {
            mainExecutor.execute(new BookmarkDeleteRunnable(dialog, bookmarkId));
        }
    }

    @Override
    public void onError(Throwable error) {
    }
}
