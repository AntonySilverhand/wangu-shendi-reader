package org.wanshu.reader.ui.bookmarks;

import org.wanshu.reader.data.personal.entity.BookmarkEntity;

public interface BookmarkItemClickListener {
    void onBookmarkClicked(BookmarkEntity bookmark);
    void onBookmarkDeleteClicked(BookmarkEntity bookmark);
}
