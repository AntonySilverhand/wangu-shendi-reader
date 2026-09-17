package org.wanshu.reader.ui.bookmarks;

import java.util.concurrent.Executor;
import org.wanshu.reader.data.repository.CompletionCallback;

public class BookmarksClearedCallback implements CompletionCallback {
    private final BookmarksDialog dialog;
    private final Executor mainExecutor;

    public BookmarksClearedCallback(BookmarksDialog dialog, Executor mainExecutor) {
        this.dialog = dialog;
        this.mainExecutor = mainExecutor;
    }

    @Override
    public void onSuccess() {
        if (mainExecutor != null) {
            mainExecutor.execute(new BookmarksClearedRunnable(dialog));
        }
    }

    @Override
    public void onError(Throwable error) {
    }
}
