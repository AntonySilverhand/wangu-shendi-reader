package org.wanshu.reader.ui.cover;

import android.graphics.Bitmap;
import android.widget.ImageView;

/** Main thread: shows the bitmap only when the view still belongs to the same book. */
public class CoverApplyRunnable implements Runnable {
    private final ImageView view;
    private final String bookId;
    private final Bitmap bitmap;

    public CoverApplyRunnable(ImageView view, String bookId, Bitmap bitmap) {
        this.view = view;
        this.bookId = bookId;
        this.bitmap = bitmap;
    }

    @Override
    public void run() {
        if (bookId.equals(view.getTag())) {
            view.setImageBitmap(bitmap);
        }
    }
}
