package org.wanshu.reader.ui.bookmarks;

import android.view.View;
import org.wanshu.reader.data.personal.entity.BookmarkEntity;

public class BookmarkDeleteClickListener implements View.OnClickListener {
    private final BookmarkItemClickListener listener;
    private final BookmarkEntity bookmark;

    public BookmarkDeleteClickListener(BookmarkItemClickListener listener, BookmarkEntity bookmark) {
        this.listener = listener;
        this.bookmark = bookmark;
    }

    @Override
    public void onClick(View v) {
        if (listener != null && bookmark != null) {
            listener.onBookmarkDeleteClicked(bookmark);
        }
    }
}
