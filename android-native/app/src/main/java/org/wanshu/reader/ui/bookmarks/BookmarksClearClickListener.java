package org.wanshu.reader.ui.bookmarks;

import android.view.View;

public class BookmarksClearClickListener implements View.OnClickListener {
    private final BookmarksDialog dialog;

    public BookmarksClearClickListener(BookmarksDialog dialog) {
        this.dialog = dialog;
    }

    @Override
    public void onClick(View v) {
        if (dialog != null) {
            dialog.clearAllBookmarks();
        }
    }
}
