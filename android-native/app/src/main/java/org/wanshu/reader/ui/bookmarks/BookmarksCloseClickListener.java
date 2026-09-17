package org.wanshu.reader.ui.bookmarks;

import android.view.View;

public class BookmarksCloseClickListener implements View.OnClickListener {
    private final BookmarksDialog dialog;

    public BookmarksCloseClickListener(BookmarksDialog dialog) {
        this.dialog = dialog;
    }

    @Override
    public void onClick(View v) {
        if (dialog != null) {
            dialog.dismiss();
        }
    }
}
