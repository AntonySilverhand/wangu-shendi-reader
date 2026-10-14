package org.wanshu.reader.data.repository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.wanshu.reader.core.model.TocEntry;
import org.wanshu.reader.core.net.CancellationToken;
import org.wanshu.reader.core.net.HttpFetchResponse;
import org.wanshu.reader.core.queue.RequestKey;
import org.wanshu.reader.core.queue.RequestPriority;
import org.wanshu.reader.core.queue.SchedulerAction;
import org.wanshu.reader.core.queue.SchedulerCallback;
import org.wanshu.reader.core.source.ClassifiedToc;
import org.wanshu.reader.core.source.SourceParser;
import org.wanshu.reader.core.source.SourceUrls;
import org.wanshu.reader.core.source.TocMerger;
import org.wanshu.reader.data.content.dao.TocDao;
import org.wanshu.reader.data.content.entity.TocEntryEntity;
import org.wanshu.reader.data.content.entity.TocPageEntity;

/** One page in flight per explicit load; DB progress survives close/reopen and failed pages remain retryable. */
public final class TocLoadJob implements Runnable, SchedulerAction<ClassifiedToc>, SchedulerCallback<ClassifiedToc> {
    private final NativeTocLoader loader;
    private final String bookId;
    private final Object owner;
    private final TocLoadListener listener;
    private final int pageLimit;
    private volatile boolean cancelled;
    private int page = 1;
    /** 0 while the number of TOC pages is not known yet. */
    private int total = 0;
    private volatile Integer discoveredTotal;
    private volatile ClassifiedToc result;
    private volatile Throwable failure;

    /** pageLimit 0 loads every page; a positive limit stops after that many pages (1 = only the detail page). */
    public TocLoadJob(NativeTocLoader loader, String bookId, Object owner, TocLoadListener listener, int pageLimit) {
        this.loader = loader; this.bookId = bookId; this.owner = owner; this.listener = listener; this.pageLimit = pageLimit;
    }
    public synchronized void cancel() {
        cancelled = true; loader.scheduler.cancelAllForSubscriber(this);
    }
    @Override public void run() {
        if (cancelled) return;
        TocDao dao = loader.db.tocDao();
        try {
            if (result != null || failure != null) {
                if (failure != null) {
                    TocPageEntity row = new TocPageEntity(); row.bookId = bookId; row.pageIndex = page;
                    row.status = "FAILED"; row.error = failure.getMessage() == null ? "目录加载失败" : failure.getMessage();
                    row.updatedAt = System.currentTimeMillis(); dao.insertTocPage(row);
                    listener.onProgress(dao.getLoadedPagesCount(bookId), total, true, row.error);
                    loader.finished(owner, this); return;
                }
                persistPage(dao);
                result = null; page++;
            }
            List<TocPageEntity> pages = dao.getAllTocPages(bookId);
            if (!pages.isEmpty()) total = TocPaging.totalFromRows(total, pages.get(pages.size() - 1).pageIndex);
            if (discoveredTotal != null) total = discoveredTotal;
            int bound = TocPaging.bound(total, pageLimit);
            while (page <= bound) {
                TocPageEntity cached = dao.getTocPage(bookId, page);
                if (cached == null || !"LOADED".equals(cached.status)) break;
                page++;
            }
            boolean done = page > bound;
            listener.onProgress(dao.getLoadedPagesCount(bookId), total, done, null);
            if (done) { loader.finished(owner, this); return; }
            RequestPriority priority = pageLimit > 0 ? RequestPriority.NORMAL : RequestPriority.LOW;
            synchronized (this) {
                if (!cancelled) loader.scheduler.enqueue(RequestKey.tocPage(bookId, page), priority, this, this, this);
            }
        } catch (Exception error) {
            if (!cancelled) listener.onProgress(dao.getLoadedPagesCount(bookId), total, true, error.getMessage());
            loader.finished(owner, this);
        }
    }
    private void persistPage(TocDao dao) {
        total = TocPaging.totalAfterPersist(total, discoveredTotal, page);
        loader.db.beginTransaction();
        try {
            List<TocEntry> existing = new ArrayList<TocEntry>();
            for (TocEntryEntity e : dao.getEntries(bookId)) {
                if (e.sourcePageIndex != page) existing.add(new TocEntry(e.bookId, e.chapterId, e.title, e.orderKey, e.isExtra, e.sourcePageIndex));
            }
            List<TocEntry> incoming = new ArrayList<TocEntry>(result.getMain());
            incoming.addAll(result.getExtras());
            List<TocEntry> merged = TocMerger.mergeTocPages(Arrays.asList(existing, incoming));
            List<TocEntryEntity> rows = new ArrayList<TocEntryEntity>();
            for (int i = 0; i < merged.size(); i++) {
                TocEntry e = merged.get(i); TocEntryEntity row = new TocEntryEntity();
                row.bookId = bookId; row.chapterId = e.getChapterId(); row.title = e.getTitle();
                row.orderKey = i; row.isExtra = e.isExtra(); row.sourcePageIndex = e.getSourcePageIndex(); rows.add(row);
            }
            dao.deleteEntriesForBook(bookId); dao.insertEntries(rows);
            int rowsUpTo = TocPaging.rowsUpTo(total, page);
            for (int p = 1; p <= rowsUpTo; p++) {
                TocPageEntity row = dao.getTocPage(bookId, p);
                if (row == null) { row = new TocPageEntity(); row.bookId = bookId; row.pageIndex = p; dao.insertTocPage(row); }
            }
            dao.updateTocPage(bookId, page, "LOADED", "", System.currentTimeMillis());
            loader.db.setTransactionSuccessful();
        } finally { loader.db.endTransaction(); }
    }
    @Override public ClassifiedToc execute(CancellationToken token) throws Exception {
        if (cancelled || token.isCancelled()) throw new IOException("cancelled");
        HttpFetchResponse response = loader.client.fetch(SourceUrls.buildTocUrl(bookId, page), token);
        if (response.isRateLimited()) loader.scheduler.setCooldownSeconds(response.getRetryAfterSeconds() == null ? 60 : response.getRetryAfterSeconds());
        if (!response.isSuccessful()) throw new IOException("目录 HTTP " + response.getStatusCode());
        Integer count = SourceParser.parseTocTotalPages(bookId, response.getBody());
        if (count != null) discoveredTotal = TocPaging.clampDiscovered(count);
        ClassifiedToc parsed = SourceParser.classifyTocItems(bookId, SourceParser.parseTocPageHtml(bookId, response.getBody(), page), page);
        if (parsed.getMain().isEmpty() && parsed.getExtras().isEmpty()) throw new IOException("目录内容异常；未标记完成");
        return parsed;
    }
    @Override public void onSuccess(ClassifiedToc value) { if (!cancelled) { result = value; loader.executor.execute(this); } }
    @Override public void onError(Throwable error) { if (!cancelled) { failure = error; loader.executor.execute(this); } }
    @Override public void onCancelled() { cancelled = true; loader.finished(owner, this); }
}
