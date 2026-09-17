package org.wanshu.reader.data.txt;

import android.content.Context;
import android.net.Uri;
import java.io.InputStream;
import org.wanshu.reader.core.text.TextBlockBuilder;
import org.wanshu.reader.data.content.ContentDatabase;

public class TxtImportTaskRunnable implements Runnable {
    private final Context context;
    private final Uri uri;
    private final String bookTitle;
    private final ContentDatabase db;
    private final TextBlockBuilder blockBuilder;
    private final TxtImportListener listener;

    public TxtImportTaskRunnable(
            Context context,
            Uri uri,
            String bookTitle,
            ContentDatabase db,
            TextBlockBuilder blockBuilder,
            TxtImportListener listener
    ) {
        this.context = context;
        this.uri = uri;
        this.bookTitle = bookTitle;
        this.db = db;
        this.blockBuilder = blockBuilder;
        this.listener = listener;
    }

    @Override
    public void run() {
        InputStream in = null;
        try {
            in = context.getContentResolver().openInputStream(uri);
            if (in == null) {
                throw new IllegalStateException("无法打开文件输入流: " + uri);
            }
            TxtImporter.importStream(in, bookTitle, db, blockBuilder, listener);
        } catch (Throwable t) {
            if (listener != null) {
                listener.onError(t);
            }
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {}
            }
        }
    }
}
