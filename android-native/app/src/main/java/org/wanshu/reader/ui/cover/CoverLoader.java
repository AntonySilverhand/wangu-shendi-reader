package org.wanshu.reader.ui.cover;

import android.widget.ImageView;
import java.io.File;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.wanshu.reader.core.model.BookKey;
import org.wanshu.reader.core.net.SourceHttpClient;
import org.wanshu.reader.core.queue.RequestKey;
import org.wanshu.reader.core.queue.RequestPriority;
import org.wanshu.reader.core.queue.RequestScheduler;
import org.wanshu.reader.core.source.FetchCoverBytesAction;
import org.wanshu.reader.data.AppExecutors;

/**
 * Loads book covers into ImageViews. Files are cached under cacheDir/covers/{bookId}.jpg; a missing file is
 * downloaded through the shared request queue at BACKGROUND priority. Decoding runs on the CPU executor and
 * the result is applied on the main thread only if the view still shows the same book.
 */
public class CoverLoader {
    private final File coverDir;
    private final RequestScheduler scheduler;
    private final SourceHttpClient client;
    private final AppExecutors executors;
    private final Set<String> failed = ConcurrentHashMap.newKeySet();
    /** Views with a download in flight; each view is its own scheduler subscriber so two views of one book both get the cover. */
    private final Set<ImageView> pendingViews = ConcurrentHashMap.newKeySet();

    public CoverLoader(File cacheDir, RequestScheduler scheduler, SourceHttpClient client, AppExecutors executors) {
        this.coverDir = new File(cacheDir, "covers");
        this.scheduler = scheduler;
        this.client = client;
        this.executors = executors;
    }

    /** Shows the cover of an online book in the view, once it is available. Local books have no cover. */
    public void load(ImageView view, String bookId, int targetWidthPx, int targetHeightPx) {
        view.setTag(bookId);
        view.setImageDrawable(null);
        if (!BookKey.isOnlineId(bookId) || failed.contains(bookId)) {
            return;
        }
        executors.getCpuExecutor().execute(new CoverLoadRunnable(this, view, bookId, targetWidthPx, targetHeightPx));
    }

    /** Drops cover downloads that have not started yet (called when the app leaves the foreground). */
    public void cancelPending() {
        for (ImageView view : pendingViews) {
            scheduler.cancelAllForSubscriber(view);
        }
        pendingViews.clear();
    }

    File fileFor(String bookId) {
        return new File(coverDir, bookId + ".jpg");
    }

    File directory() {
        return coverDir;
    }

    Executor cpu() {
        return executors.getCpuExecutor();
    }

    Executor main() {
        return executors.getMainThreadExecutor();
    }

    void markFailed(String bookId) {
        failed.add(bookId);
    }

    void finishedDownload(ImageView view) {
        pendingViews.remove(view);
    }

    void download(ImageView view, String bookId, int targetWidthPx, int targetHeightPx) {
        pendingViews.add(view);
        scheduler.enqueue(
                RequestKey.cover(bookId),
                RequestPriority.BACKGROUND,
                view,
                new FetchCoverBytesAction(bookId, client),
                new CoverFetchCallback(this, view, bookId, targetWidthPx, targetHeightPx)
        );
    }
}
