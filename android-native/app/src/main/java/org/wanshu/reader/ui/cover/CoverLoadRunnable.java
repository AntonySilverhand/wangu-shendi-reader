package org.wanshu.reader.ui.cover;

import android.graphics.Bitmap;
import android.widget.ImageView;
import java.io.File;

/** CPU executor: shows the cached cover file, or starts the download when there is none. */
public class CoverLoadRunnable implements Runnable {
    private final CoverLoader loader;
    private final ImageView view;
    private final String bookId;
    private final int widthPx;
    private final int heightPx;

    public CoverLoadRunnable(CoverLoader loader, ImageView view, String bookId, int widthPx, int heightPx) {
        this.loader = loader;
        this.view = view;
        this.bookId = bookId;
        this.widthPx = widthPx;
        this.heightPx = heightPx;
    }

    @Override
    public void run() {
        if (!bookId.equals(view.getTag())) {
            return; // the view moved on to another book while this was queued
        }
        File file = loader.fileFor(bookId);
        if (file.isFile()) {
            Bitmap bitmap = CoverDecoder.decode(file, widthPx, heightPx);
            if (bitmap != null) {
                loader.main().execute(new CoverApplyRunnable(view, bookId, bitmap));
                return;
            }
            file.delete(); // corrupt cache entry, fetch again
        }
        loader.download(view, bookId, widthPx, heightPx);
    }
}
