package org.wanshu.reader.ui.cover;

import android.widget.ImageView;
import org.wanshu.reader.core.queue.SchedulerCallback;

/** Called by the request scheduler; hands the downloaded bytes to the CPU executor so the queue is never blocked. */
public class CoverFetchCallback implements SchedulerCallback<byte[]> {
    private final CoverLoader loader;
    private final ImageView view;
    private final String bookId;
    private final int widthPx;
    private final int heightPx;

    public CoverFetchCallback(CoverLoader loader, ImageView view, String bookId, int widthPx, int heightPx) {
        this.loader = loader;
        this.view = view;
        this.bookId = bookId;
        this.widthPx = widthPx;
        this.heightPx = heightPx;
    }

    @Override
    public void onSuccess(byte[] bytes) {
        loader.finishedDownload(view);
        loader.cpu().execute(new CoverStoreRunnable(loader, view, bookId, bytes, widthPx, heightPx));
    }

    @Override
    public void onError(Throwable error) {
        loader.finishedDownload(view);
        loader.markFailed(bookId);
    }

    @Override
    public void onCancelled() {
        loader.finishedDownload(view);
        // dropped while waiting; the next display tries again
    }
}
