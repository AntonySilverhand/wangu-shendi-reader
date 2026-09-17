package org.wanshu.reader.core.toc;

import java.util.ArrayList;
import java.util.List;
import org.wanshu.reader.core.model.TocEntry;
import org.wanshu.reader.core.net.CancellationToken;

public class FakeTocSearchDeps implements TocSearchDeps {
    public final FakeTocStore store;

    public FakeTocSearchDeps(FakeTocStore store) {
        this.store = store;
    }

    @Override
    public List<TocSearchResult> searchLoaded(String query, int limit) {
        store.searchLog.add(query);
        String q = query.trim();
        Integer numeric = null;
        try {
            numeric = Integer.parseInt(q);
        } catch (NumberFormatException ignored) {}

        String lower = q.toLowerCase();
        List<TocSearchResult> out = new ArrayList<TocSearchResult>();
        List<TocEntry> all = store.allEntries();

        for (int i = 0; i < all.size() && out.size() < limit; i++) {
            TocEntry e = all.get(i);
            boolean titleMatch = e.getTitle().toLowerCase().contains(lower);
            boolean numMatch = numeric != null && (e.getOrderKey() == numeric.longValue() || e.getChapterId().equals(q));
            if (titleMatch || numMatch) {
                out.add(new TocSearchResult(i, e));
            }
        }
        return out;
    }

    @Override
    public int countLoadable() {
        int n = 0;
        for (int p = 1; p <= store.totalPages; p++) {
            if (!store.loaded.containsKey(p) && !store.failed.contains(p) && !store.loading.contains(p)) {
                n++;
            }
        }
        return n;
    }

    @Override
    public Integer nextLoadablePage() {
        for (int p = 1; p <= store.totalPages; p++) {
            if (!store.loaded.containsKey(p) && !store.failed.contains(p) && !store.loading.contains(p)) {
                return p;
            }
        }
        return null;
    }

    @Override
    public boolean isComplete() {
        return store.loaded.size() >= store.totalPages;
    }

    @Override
    public int failedPageCount() {
        return store.failed.size();
    }

    @Override
    public int loadedPageCount() {
        return store.loaded.size();
    }

    @Override
    public int totalPages() {
        return store.totalPages;
    }

    @Override
    public void loadRange(int from, int to, CancellationToken token) throws Exception {
        store.loadLog.add(from + "-" + to);
        for (int p = from; p <= to; p++) {
            if (token != null && token.isCancelled()) {
                throw new IllegalStateException("Aborted");
            }
            if (store.loaded.containsKey(p) || store.failed.contains(p)) {
                continue;
            }
            store.loaded.put(p, store.entriesOf(p));
        }
    }

    @Override
    public Integer estimatePageForNumber(int number) {
        int page = (number + store.perPage - 1) / store.perPage;
        return Math.max(1, Math.min(store.totalPages, page));
    }

    @Override
    public void retryFailedPages(CancellationToken token) throws Exception {
        List<Integer> failedPages = new ArrayList<Integer>(store.failed);
        for (int i = 0; i < failedPages.size(); i++) {
            if (token != null && token.isCancelled()) {
                throw new IllegalStateException("Aborted");
            }
            int p = failedPages.get(i);
            store.failed.remove(p);
            store.loaded.put(p, store.entriesOf(p));
        }
    }
}
