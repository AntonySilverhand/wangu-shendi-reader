package org.wanshu.reader.ui.bookmarks;

import android.view.View;
import org.wanshu.reader.data.personal.entity.BookmarkEntity;

public class BookmarkItemClickListener implements View.OnClickListener {
    private final BookmarksDialog dialog;
    private final BookmarkEntity bookmark;

    public BookmarkItemClickListener(BookmarksDialog dialog, BookmarkEntity bookmark) {
        this.dialog = dialog;
        this.bookmark = bookmark;
    }

    @Override
    public void onClick(View v) {
        if (dialog != null && bookmark != null) {
            dialog.onBookmarkClicked(bookmark);
        }
    }
}
