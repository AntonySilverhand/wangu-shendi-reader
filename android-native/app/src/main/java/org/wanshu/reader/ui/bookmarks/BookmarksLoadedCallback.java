package org.wanshu.reader.ui.bookmarks;

import java.util.List;
import org.wanshu.reader.data.personal.entity.BookmarkEntity;
import org.wanshu.reader.data.repository.DataCallback;

public class BookmarksLoadedCallback implements DataCallback<List<BookmarkEntity>> {
    private final BookmarksDialog dialog;

    public BookmarksLoadedCallback(BookmarksDialog dialog) {
        this.dialog = dialog;
    }

    @Override
    public void onSuccess(List<BookmarkEntity> result) {
        if (dialog != null) {
            dialog.onBookmarksLoaded(result);
        }
    }

    @Override
    public void onError(Throwable error) {
        if (dialog != null) {
            dialog.onBookmarksLoadError(error != null ? error.getMessage() : "加载书签失败");
        }
    }
}
