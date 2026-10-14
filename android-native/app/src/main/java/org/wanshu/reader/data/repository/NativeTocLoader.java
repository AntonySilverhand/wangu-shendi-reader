package org.wanshu.reader.data.repository;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.wanshu.reader.core.model.BookKey;
import org.wanshu.reader.core.net.SourceHttpClient;
import org.wanshu.reader.core.queue.RequestScheduler;
import org.wanshu.reader.data.content.ContentDatabase;

/** Explicit user-requested TOC loading; every physical page uses the shared source queue. */
public final class NativeTocLoader {
    final ContentDatabase db;
    final RequestScheduler scheduler;
    final SourceHttpClient client;
    final Executor executor;
    private final ConcurrentHashMap<Object, TocLoadJob> jobs = new ConcurrentHashMap<Object, TocLoadJob>();
    public NativeTocLoader(ContentDatabase db, RequestScheduler scheduler, SourceHttpClient client, Executor executor) {
        this.db = db; this.scheduler = scheduler; this.client = client; this.executor = executor;
    }
    /** Loads the whole TOC of an online book, page by page. */
    public void load(String bookId, Object owner, TocLoadListener listener) {
        start(bookId, owner, listener, 0);
    }
    /** Loads only the first TOC page, which is enough to find the first chapter. */
    public void loadFirstPage(String bookId, Object owner, TocLoadListener listener) {
        start(bookId, owner, listener, 1);
    }
    private void start(String bookId, Object owner, TocLoadListener listener, int pageLimit) {
        if (!BookKey.isOnlineId(bookId)) throw new IllegalArgumentException("Only online books can load a TOC");
        cancel(owner);
        TocLoadJob job = new TocLoadJob(this, bookId, owner, listener, pageLimit);
        jobs.put(owner, job); executor.execute(job);
    }
    public void cancel(Object owner) {
        TocLoadJob job = jobs.remove(owner);
        if (job != null) job.cancel();
    }
    void finished(Object owner, TocLoadJob job) { jobs.remove(owner, job); }
}
