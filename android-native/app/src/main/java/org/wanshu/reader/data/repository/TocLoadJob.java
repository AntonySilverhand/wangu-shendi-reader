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
    private final Object owner;
    private final TocLoadListener listener;
    private volatile boolean cancelled;
    private int page = 1, total = 44;
    private volatile Integer discoveredTotal;
    private volatile ClassifiedToc result;
    private volatile Throwable failure;
    public TocLoadJob(NativeTocLoader loader, Object owner, TocLoadListener listener) {
        this.loader = loader; this.owner = owner; this.listener = listener;
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
                    TocPageEntity row = new TocPageEntity(); row.bookId = "36780"; row.pageIndex = page;
                    row.status = "FAILED"; row.error = failure.getMessage() == null ? "目录加载失败" : failure.getMessage();
                    row.updatedAt = System.currentTimeMillis(); dao.insertTocPage(row);
                    listener.onProgress(dao.getLoadedPagesCount("36780"), total, true, row.error);
                    loader.finished(owner, this); return;
                }
                persistPage(dao);
                result = null; page++;
            }
            List<TocPageEntity> pages = dao.getAllTocPages("36780");
            if (!pages.isEmpty()) total = pages.get(pages.size() - 1).pageIndex;
            if (discoveredTotal != null) total = discoveredTotal;
            while (page <= total) {
                TocPageEntity cached = dao.getTocPage("36780", page);
                if (cached == null || !"LOADED".equals(cached.status)) break;
                page++;
            }
            boolean done = page > total;
            listener.onProgress(dao.getLoadedPagesCount("36780"), total, done, null);
            if (done) { loader.finished(owner, this); return; }
            synchronized (this) {
                if (!cancelled) loader.scheduler.enqueue(RequestKey.tocPage("36780", page), RequestPriority.LOW, this, this, this);
            }
        } catch (Exception error) {
            if (!cancelled) listener.onProgress(dao.getLoadedPagesCount("36780"), total, true, error.getMessage());
            loader.finished(owner, this);
        }
    }
    private void persistPage(TocDao dao) {
        if (discoveredTotal != null) total = discoveredTotal;
        loader.db.beginTransaction();
        try {
            List<TocEntry> existing = new ArrayList<TocEntry>();
            for (TocEntryEntity e : dao.getEntries("36780")) {
                if (e.sourcePageIndex != page) existing.add(new TocEntry(e.bookId, e.chapterId, e.title, e.orderKey, e.isExtra, e.sourcePageIndex));
            }
            List<TocEntry> incoming = new ArrayList<TocEntry>(result.getMain());
            incoming.addAll(result.getExtras());
            List<TocEntry> merged = TocMerger.mergeTocPages(Arrays.asList(existing, incoming));
            List<TocEntryEntity> rows = new ArrayList<TocEntryEntity>();
            for (int i = 0; i < merged.size(); i++) {
                TocEntry e = merged.get(i); TocEntryEntity row = new TocEntryEntity();
                row.bookId = "36780"; row.chapterId = e.getChapterId(); row.title = e.getTitle();
                row.orderKey = i; row.isExtra = e.isExtra(); row.sourcePageIndex = e.getSourcePageIndex(); rows.add(row);
            }
            dao.deleteEntriesForBook("36780"); dao.insertEntries(rows);
            for (int p = 1; p <= total; p++) {
                TocPageEntity row = dao.getTocPage("36780", p);
                if (row == null) { row = new TocPageEntity(); row.bookId = "36780"; row.pageIndex = p; dao.insertTocPage(row); }
            }
            dao.updateTocPage("36780", page, "LOADED", "", System.currentTimeMillis());
            loader.db.setTransactionSuccessful();
        } finally { loader.db.endTransaction(); }
    }
    @Override public ClassifiedToc execute(CancellationToken token) throws Exception {
        if (cancelled || token.isCancelled()) throw new IOException("cancelled");
        HttpFetchResponse response = loader.client.fetch(SourceUrls.buildTocUrl(page), token);
        if (response.isRateLimited()) loader.scheduler.setCooldownSeconds(response.getRetryAfterSeconds() == null ? 60 : response.getRetryAfterSeconds());
        if (!response.isSuccessful()) throw new IOException("目录 HTTP " + response.getStatusCode());
        Integer count = SourceParser.parseTocTotalPages(response.getBody());
        if (count != null) discoveredTotal = Math.max(1, Math.min(80, count));
        ClassifiedToc parsed = SourceParser.classifyTocItems(SourceParser.parseTocPageHtml(response.getBody(), page), page);
        if (parsed.getMain().isEmpty() && parsed.getExtras().isEmpty()) throw new IOException("目录内容异常；未标记完成");
        return parsed;
    }
    @Override public void onSuccess(ClassifiedToc value) { if (!cancelled) { result = value; loader.executor.execute(this); } }
    @Override public void onError(Throwable error) { if (!cancelled) { failure = error; loader.executor.execute(this); } }
    @Override public void onCancelled() { cancelled = true; loader.finished(owner, this); }
}
