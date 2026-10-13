package org.wanshu.reader.ui.bookmarks;

import org.wanshu.reader.data.personal.entity.BookmarkEntity;
import org.wanshu.reader.data.repository.DataCallback;

public class BookmarkAddCompletedCallback implements DataCallback<BookmarkEntity> {
    private final BookmarksDialog dialog;

    public BookmarkAddCompletedCallback(BookmarksDialog dialog) {
        this.dialog = dialog;
    }

    @Override
    public void onSuccess(BookmarkEntity result) {
        if (dialog != null) {
            dialog.onBookmarkAdded(result);
        }
    }

    @Override
    public void onError(Throwable error) {
        if (dialog != null) {
            dialog.onBookmarkAddError(error != null ? error.getMessage() : "添加书签失败");
        }
    }
}
