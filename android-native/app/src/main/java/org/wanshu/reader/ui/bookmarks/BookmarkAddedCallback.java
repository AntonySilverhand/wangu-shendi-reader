package org.wanshu.reader.ui.bookmarks;

import java.util.concurrent.Executor;
import org.wanshu.reader.data.personal.entity.BookmarkEntity;
import org.wanshu.reader.data.repository.DataCallback;

public class BookmarkAddedCallback implements DataCallback<BookmarkEntity> {
    private final BookmarksDialog dialog;
    private final Executor mainExecutor;

    public BookmarkAddedCallback(BookmarksDialog dialog, Executor mainExecutor) {
        this.dialog = dialog;
        this.mainExecutor = mainExecutor;
    }

    @Override
    public void onSuccess(BookmarkEntity data) {
        if (mainExecutor != null) {
            mainExecutor.execute(new BookmarkAddedRunnable(dialog, data));
        }
    }

    @Override
    public void onError(Throwable error) {
    }
}
