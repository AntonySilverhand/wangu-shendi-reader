package org.wanshu.reader.core.toc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.wanshu.reader.core.net.CancellationToken;

public class TocSearchController {
    public static final int SEARCH_DEBOUNCE_MS = 260;
    public static final int PROBE_RADIUS = 3;
    public static final int FULL_SCAN_CHUNK = 4;
    public static final int TOC_SEARCH_LIMIT = 200;

    private final TocSearchDeps deps;
    private final TocSearchEvents events;
    private final ScheduledExecutorService executor;
    private final boolean ownsExecutor;

    private long generation = 0L;
    private ScheduledFuture<?> debounceFuture = null;
    private CancellationToken activeToken = null;
    private volatile boolean busy = false;
    private List<TocSearchResult> results = new ArrayList<TocSearchResult>();
    private boolean cancelled = false;

    public TocSearchController(TocSearchDeps deps, TocSearchEvents events, ScheduledExecutorService executor) {
        this.deps = deps;
        this.events = events;
        if (executor != null) {
            this.executor = executor;
            this.ownsExecutor = false;
        } else {
            this.executor = Executors.newSingleThreadScheduledExecutor();
            this.ownsExecutor = true;
        }
    }

    public TocSearchController(TocSearchDeps deps, TocSearchEvents events) {
        this(deps, events, null);
    }

    public List<TocSearchResult> getCurrentResults() {
        return results;
    }

    public boolean isBusy() {
        return busy;
    }

    public synchronized void setQuery(String query) {
        String q = query != null ? query.trim() : "";
        generation++;
        cancelled = false;
        abortInFlight();
        cancelDebounce();

        if (q.isEmpty()) {
            results = new ArrayList<TocSearchResult>();
            events.onUpdate(TocSearchPhase.idle(), results);
            return;
        }

        results = deps.searchLoaded(q, TOC_SEARCH_LIMIT);
        events.onUpdate(TocSearchPhase.debouncing(), results);

        final long gen = generation;
        final String finalQ = q;
        debounceFuture = executor.schedule(
                new TocSearchDebounceRunnable(this, gen, finalQ),
                SEARCH_DEBOUNCE_MS,
                TimeUnit.MILLISECONDS
        );
    }

    public synchronized void onDebounceFired(long gen, String q) {
        if (gen != generation || cancelled) {
            return;
        }
        debounceFuture = null;
        startTask(gen, q, false);
    }

    public synchronized void searchNow(String query) {
        String q = query != null ? query.trim() : "";
        generation++;
        cancelled = false;
        abortInFlight();
        cancelDebounce();

        if (q.isEmpty()) {
            results = new ArrayList<TocSearchResult>();
            events.onUpdate(TocSearchPhase.idle(), results);
            return;
        }

        startTask(generation, q, false);
    }

    public synchronized void cancel() {
        generation++;
        cancelled = true;
        abortInFlight();
        cancelDebounce();
        results = new ArrayList<TocSearchResult>();
        events.onUpdate(TocSearchPhase.cancelled(), results);
    }

    public synchronized void resumeAfterRetry(String query) {
        String q = query != null ? query.trim() : "";
        if (q.isEmpty() || busy) {
            return;
        }
        generation++;
        cancelled = false;
        abortInFlight();
        cancelDebounce();
        startTask(generation, q, true);
    }

    public synchronized void refreshFromLoadedData(String query) {
        String q = query != null ? query.trim() : "";
        if (q.isEmpty() || busy || debounceFuture != null || cancelled) {
            return;
        }
        results = deps.searchLoaded(q, TOC_SEARCH_LIMIT);
        events.onUpdate(donePhase(), results);
    }

    public void shutdown() {
        cancel();
        if (ownsExecutor) {
            executor.shutdown();
        }
    }

    private void cancelDebounce() {
        if (debounceFuture != null) {
            debounceFuture.cancel(false);
            debounceFuture = null;
        }
    }

    private void abortInFlight() {
        if (activeToken != null) {
            activeToken.cancel();
            activeToken = null;
        }
        busy = false;
    }

    private void startTask(long gen, String q, boolean retryFirst) {
        if (busy) {
            return;
        }
        CancellationToken token = new CancellationToken();
        activeToken = token;
        busy = true;

        executor.execute(new TocSearchTaskRunnable(this, gen, q, retryFirst, token));
    }

    public void executeTask(long gen, String query, boolean retryFirst, CancellationToken token) {
        try {
            if (retryFirst) {
                deps.retryFailedPages(token);
            }
            if (gen != generation || token.isCancelled()) {
                return;
            }
            runSearch(gen, query, token);
        } catch (Throwable t) {
            if (!token.isCancelled() && gen == generation) {
                finish(gen, donePhase());
            }
        } finally {
            synchronized (this) {
                if (gen == generation) {
                    busy = false;
                    activeToken = null;
                }
            }
        }
    }

    private void runSearch(long gen, String query, CancellationToken token) throws Exception {
        // 1. Search loaded
        List<TocSearchResult> immediate = deps.searchLoaded(query, TOC_SEARCH_LIMIT);
        if (gen != generation || token.isCancelled()) return;
        this.results = immediate;

        boolean isNumeric = isDigitsOnly(query);
        if (!immediate.isEmpty() && (isNumeric || deps.isComplete())) {
            events.onUpdate(donePhase(), immediate);
            return;
        }

        // 2. Pure number probe
        Integer num = isNumeric ? Integer.parseInt(query) : null;
        if (num != null && !deps.isComplete()) {
            boolean found = numericProbe(gen, query, num, token);
            if (gen != generation || token.isCancelled()) return;
            if (found) return;
        }

        // 3. Full scan
        fullScan(gen, query, token);
    }

    private boolean numericProbe(long gen, String query, int n, CancellationToken token) throws Exception {
        Integer est = deps.estimatePageForNumber(n);
        int total = deps.totalPages();
        List<Integer> pages = new ArrayList<Integer>();

        if (est != null) {
            pages.add(est);
        }
        for (int r = 1; r <= PROBE_RADIUS; r++) {
            if (est == null) break;
            if (est + r <= total) pages.add(est + r);
            if (est - r >= 1) pages.add(est - r);
        }

        for (int i = 0; i < pages.size(); i++) {
            int page = pages.get(i);
            if (gen != generation || token.isCancelled()) return false;

            events.onUpdate(
                    TocSearchPhase.searching("probe", deps.loadedPageCount(), total),
                    new ArrayList<TocSearchResult>()
            );

            deps.loadRange(page, page, token);
            if (gen != generation || token.isCancelled()) return false;

            List<TocSearchResult> res = deps.searchLoaded(query, TOC_SEARCH_LIMIT);
            if (gen != generation) return false;
            if (!res.isEmpty()) {
                this.results = res;
                events.onUpdate(donePhase(), res);
                return true;
            }
        }
        return false;
    }

    private void fullScan(long gen, String query, CancellationToken token) throws Exception {
        for (int guard = 0; guard < 400; guard++) {
            if (gen != generation || token.isCancelled()) return;

            Integer next = deps.nextLoadablePage();
            if (next == null) {
                finish(gen, donePhase());
                return;
            }

            int before = deps.countLoadable();
            events.onUpdate(
                    TocSearchPhase.searching("full", deps.loadedPageCount(), deps.totalPages()),
                    this.results
            );

            deps.loadRange(next, next + FULL_SCAN_CHUNK - 1, token);
            if (gen != generation || token.isCancelled()) return;

            List<TocSearchResult> res = deps.searchLoaded(query, TOC_SEARCH_LIMIT);
            if (gen != generation) return;
            this.results = res;

            if (!res.isEmpty() && isDigitsOnly(query)) {
                events.onUpdate(donePhase(), res);
                return;
            }

            events.onUpdate(
                    TocSearchPhase.searching("full", deps.loadedPageCount(), deps.totalPages()),
                    res
            );

            if (deps.isComplete()) {
                events.onUpdate(
                        TocSearchPhase.done(true, false, 0),
                        res
                );
                return;
            }

            // Anti-spin: if no progress made, terminate immediately
            if (deps.countLoadable() >= before) {
                finish(gen, donePhase());
                return;
            }
        }
        finish(gen, donePhase());
    }

    private void finish(long gen, TocSearchPhase state) {
        if (gen != generation) return;
        events.onUpdate(state, this.results);
    }

    private TocSearchPhase donePhase() {
        return TocSearchPhase.done(
                deps.isComplete(),
                deps.nextLoadablePage() == null,
                deps.failedPageCount()
        );
    }

    private static boolean isDigitsOnly(String str) {
        if (str == null || str.isEmpty()) return false;
        for (int i = 0; i < str.length(); i++) {
            if (!Character.isDigit(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
