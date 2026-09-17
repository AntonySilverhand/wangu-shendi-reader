package org.wanshu.reader.ui.bookmarks;

import java.util.List;
import java.util.concurrent.Executor;
import org.wanshu.reader.data.personal.entity.BookmarkEntity;
import org.wanshu.reader.data.repository.DataCallback;

public class BookmarksLoadedCallback implements DataCallback<List<BookmarkEntity>> {
    private final BookmarksDialog dialog;
    private final Executor mainExecutor;

    public BookmarksLoadedCallback(BookmarksDialog dialog, Executor mainExecutor) {
        this.dialog = dialog;
        this.mainExecutor = mainExecutor;
    }

    @Override
    public void onSuccess(List<BookmarkEntity> data) {
        if (mainExecutor != null) {
            mainExecutor.execute(new BookmarksDisplayRunnable(dialog, data));
        }
    }

    @Override
    public void onError(Throwable error) {
        if (mainExecutor != null) {
            mainExecutor.execute(new BookmarksDisplayRunnable(dialog, null));
        }
    }
}
