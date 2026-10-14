package org.wanshu.reader.ui.cover;

import android.graphics.Bitmap;
import android.widget.ImageView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** CPU executor: writes the downloaded cover into the cache (temp file, then rename) and shows it. */
public class CoverStoreRunnable implements Runnable {
    private final CoverLoader loader;
    private final ImageView view;
    private final String bookId;
    private final byte[] bytes;
    private final int widthPx;
    private final int heightPx;

    public CoverStoreRunnable(CoverLoader loader, ImageView view, String bookId, byte[] bytes, int widthPx, int heightPx) {
        this.loader = loader;
        this.view = view;
        this.bookId = bookId;
        this.bytes = bytes;
        this.widthPx = widthPx;
        this.heightPx = heightPx;
    }

    @Override
    public void run() {
        File dir = loader.directory();
        File target = loader.fileFor(bookId);
        File temp = new File(dir, bookId + ".jpg.tmp");
        try {
            if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) {
                throw new IOException("cannot create cover cache");
            }
            FileOutputStream out = new FileOutputStream(temp);
            try {
                out.write(bytes);
            } finally {
                out.close();
            }
            Bitmap bitmap = CoverDecoder.decode(temp, widthPx, heightPx);
            if (bitmap == null) {
                temp.delete();
                loader.markFailed(bookId);
                return;
            }
            if (!temp.renameTo(target)) {
                temp.delete();
            }
            loader.main().execute(new CoverApplyRunnable(view, bookId, bitmap));
        } catch (IOException e) {
            temp.delete();
            loader.markFailed(bookId);
        }
    }
}
