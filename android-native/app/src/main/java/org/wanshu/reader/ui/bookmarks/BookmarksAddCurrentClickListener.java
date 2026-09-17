package org.wanshu.reader.ui.bookmarks;

import android.view.View;

public class BookmarksAddCurrentClickListener implements View.OnClickListener {
    private final BookmarksDialog dialog;

    public BookmarksAddCurrentClickListener(BookmarksDialog dialog) {
        this.dialog = dialog;
    }

    @Override
    public void onClick(View v) {
        if (dialog != null) {
            dialog.addCurrentBookmark();
        }
    }
}
