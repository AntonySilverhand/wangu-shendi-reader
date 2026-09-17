package org.wanshu.reader.ui.toc;

import java.util.ArrayList;
import java.util.List;
import org.wanshu.reader.core.model.TocEntry;
import org.wanshu.reader.core.net.CancellationToken;
import org.wanshu.reader.core.net.SourceHttpClient;
import org.wanshu.reader.core.source.ClassifiedToc;
import org.wanshu.reader.core.source.SourceParser;
import org.wanshu.reader.core.source.SourceUrls;
import org.wanshu.reader.core.source.TocItemRaw;
import org.wanshu.reader.core.source.TocMerger;
import org.wanshu.reader.core.toc.TocSearchDeps;
import org.wanshu.reader.core.toc.TocSearchResult;
import org.wanshu.reader.data.content.ContentDatabase;
import org.wanshu.reader.data.content.entity.TocEntryEntity;
import org.wanshu.reader.data.content.entity.TocPageEntity;

public class ContentTocSearchDeps implements TocSearchDeps {
    public static final int ONLINE_TOTAL_PAGES = 44;

    private final String bookId;
    private final ContentDatabase db;
    private final SourceHttpClient httpClient;

    public ContentTocSearchDeps(String bookId, ContentDatabase db, SourceHttpClient httpClient) {
        this.bookId = bookId != null ? bookId : "";
        this.db = db;
        this.httpClient = httpClient;
    }

    private boolean isLocalBook() {
        return bookId.startsWith("local-");
    }

    @Override
    public List<TocSearchResult> searchLoaded(String query, int limit) {
        List<TocSearchResult> matches = new ArrayList<TocSearchResult>();
        if (query == null || query.trim().isEmpty()) {
            return matches;
        }

        String q = query.trim().toLowerCase();
        List<TocEntryEntity> entries = db.tocDao().getEntries(bookId);
        if (entries != null) {
            for (int i = 0; i < entries.size() && matches.size() < limit; i++) {
                TocEntryEntity e = entries.get(i);
                if (e.title != null && e.title.toLowerCase().contains(q)) {
                    matches.add(new TocSearchResult(
                            i,
                            new TocEntry(e.bookId, e.chapterId, e.title, e.orderKey, e.isExtra, e.sourcePageIndex)
                    ));
                }
            }
        }
        return matches;
    }

    @Override
    public int countLoadable() {
        if (isLocalBook()) {
            return 0;
        }
        return Math.max(0, ONLINE_TOTAL_PAGES - loadedPageCount());
    }

    @Override
    public Integer nextLoadablePage() {
        if (isLocalBook()) {
            return null;
        }
        List<TocPageEntity> pending = db.tocDao().getPendingTocPages(bookId);
        if (pending != null && !pending.isEmpty()) {
            return pending.get(0).pageIndex;
        }
        int loaded = loadedPageCount();
        if (loaded < ONLINE_TOTAL_PAGES) {
            return loaded + 1;
        }
        return null;
    }

    @Override
    public boolean isComplete() {
        if (isLocalBook()) {
            return true;
        }
        return loadedPageCount() >= ONLINE_TOTAL_PAGES && failedPageCount() == 0;
    }

    @Override
    public int failedPageCount() {
        if (isLocalBook()) {
            return 0;
        }
        List<TocPageEntity> pages = db.tocDao().getAllTocPages(bookId);
        int count = 0;
        if (pages != null) {
            for (int i = 0; i < pages.size(); i++) {
                if ("FAILED".equals(pages.get(i).status)) {
                    count++;
                }
            }
        }
        return count;
    }

    @Override
    public int loadedPageCount() {
        if (isLocalBook()) {
            return 1;
        }
        return db.tocDao().getLoadedPagesCount(bookId);
    }

    @Override
    public int totalPages() {
        if (isLocalBook()) {
            return 1;
        }
        return ONLINE_TOTAL_PAGES;
    }

    @Override
    public Integer estimatePageForNumber(int number) {
        if (isLocalBook()) {
            return null;
        }
        if (number <= 0) return 1;
        int page = (number / 100) + 1;
        if (page < 1) page = 1;
        if (page > ONLINE_TOTAL_PAGES) page = ONLINE_TOTAL_PAGES;
        return page;
    }

    @Override
    public void loadRange(int from, int to, CancellationToken token) throws Exception {
        if (isLocalBook() || httpClient == null) {
            return;
        }

        int start = Math.max(1, from);
        int end = Math.min(ONLINE_TOTAL_PAGES, to);

        for (int p = start; p <= end; p++) {
            if (token != null && token.isCancelled()) {
                break;
            }

            // Check if page already loaded
            TocPageEntity pageEnt = db.tocDao().getTocPage(bookId, p);
            if (pageEnt != null && "LOADED".equals(pageEnt.status)) {
                continue;
            }

            try {
                String url = SourceUrls.buildTocUrl(p);
                org.wanshu.reader.core.net.HttpFetchResponse resp = httpClient.fetch(url, token);
                String html = resp.getBody();
                List<TocItemRaw> rawItems = SourceParser.parseTocPageHtml(html, p);
                ClassifiedToc classified = SourceParser.classifyTocItems(rawItems, p);

                List<TocEntryEntity> entities = new ArrayList<TocEntryEntity>();
                for (int i = 0; i < classified.getMain().size(); i++) {
                    TocEntry entry = classified.getMain().get(i);
                    TocEntryEntity entity = new TocEntryEntity();
                    entity.bookId = bookId;
                    entity.chapterId = entry.getChapterId();
                    entity.title = entry.getTitle();
                    entity.orderKey = entry.getOrderKey();
                    entity.isExtra = entry.isExtra();
                    entity.sourcePageIndex = entry.getSourcePageIndex();
                    entities.add(entity);
                }
                for (int i = 0; i < classified.getExtras().size(); i++) {
                    TocEntry entry = classified.getExtras().get(i);
                    TocEntryEntity entity = new TocEntryEntity();
                    entity.bookId = bookId;
                    entity.chapterId = entry.getChapterId();
                    entity.title = entry.getTitle();
                    entity.orderKey = entry.getOrderKey();
                    entity.isExtra = entry.isExtra();
                    entity.sourcePageIndex = entry.getSourcePageIndex();
                    entities.add(entity);
                }

                db.tocDao().insertEntries(entities);

                TocPageEntity loadedPage = new TocPageEntity();
                loadedPage.bookId = bookId;
                loadedPage.pageIndex = p;
                loadedPage.status = "LOADED";
                loadedPage.error = null;
                loadedPage.updatedAt = System.currentTimeMillis();
                db.tocDao().insertTocPage(loadedPage);
            } catch (Exception e) {
                TocPageEntity failedPage = new TocPageEntity();
                failedPage.bookId = bookId;
                failedPage.pageIndex = p;
                failedPage.status = "FAILED";
                failedPage.error = e.getMessage();
                failedPage.updatedAt = System.currentTimeMillis();
                db.tocDao().insertTocPage(failedPage);
            }
        }
    }

    @Override
    public void retryFailedPages(CancellationToken token) throws Exception {
        if (isLocalBook() || httpClient == null) {
            return;
        }

        List<TocPageEntity> pages = db.tocDao().getAllTocPages(bookId);
        if (pages == null) return;

        for (int i = 0; i < pages.size(); i++) {
            if (token != null && token.isCancelled()) {
                break;
            }
            TocPageEntity page = pages.get(i);
            if ("FAILED".equals(page.status)) {
                loadRange(page.pageIndex, page.pageIndex, token);
            }
        }
    }
}
