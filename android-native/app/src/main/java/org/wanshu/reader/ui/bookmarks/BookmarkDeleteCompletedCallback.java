package org.wanshu.reader.ui.bookmarks;

import org.wanshu.reader.data.repository.CompletionCallback;

public class BookmarkDeleteCompletedCallback implements CompletionCallback {
    private final BookmarksDialog dialog;

    public BookmarkDeleteCompletedCallback(BookmarksDialog dialog) {
        this.dialog = dialog;
    }

    @Override
    public void onSuccess() {
        if (dialog != null) {
            dialog.onBookmarkDeleted();
        }
    }

    @Override
    public void onError(Throwable error) {
        if (dialog != null) {
            dialog.onBookmarkDeleteError(error != null ? error.getMessage() : "删除书签失败");
        }
    }
}
