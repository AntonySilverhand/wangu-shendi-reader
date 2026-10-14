package org.wanshu.reader.data.repository;

/**
 * Paging rules for loading a book's TOC page by page when the number of pages is not known up front.
 * Total 0 means unknown. Page 1 (the detail page) has no page selector, so the first two pages are always
 * loaded; page 2 reveals the total (a single-option selector means a one-page TOC).
 */
public final class TocPaging {
    /** Same ceiling as the URL validator's TOC sub-page range (/book/{id}/999.html is page 1000). */
    public static final int MAX_TOC_PAGES = 1000;
    public static final int PROBE_PAGES = 2;

    private TocPaging() {}

    public static int clampDiscovered(int count) {
        return Math.max(1, Math.min(MAX_TOC_PAGES, count));
    }

    /** Page rows 1..total only exist once the total is known and above 1; a lone row 1 says nothing. */
    public static int totalFromRows(int currentTotal, int lastRowPageIndex) {
        return lastRowPageIndex >= PROBE_PAGES ? lastRowPageIndex : currentTotal;
    }

    /** Last page this load may fetch. pageLimit 0 means no limit. */
    public static int bound(int total, int pageLimit) {
        int bound = total > 0 ? total : PROBE_PAGES;
        return pageLimit > 0 ? Math.min(bound, pageLimit) : bound;
    }

    /** Total after page {@code page} was saved: the discovered one, else stop after the probe pages. */
    public static int totalAfterPersist(int total, Integer discovered, int page) {
        if (discovered != null) return discovered;
        if (total <= 0 && page >= PROBE_PAGES) return page;
        return total;
    }

    /** Page rows to create when saving: all of them once the total is known, else just up to the saved page. */
    public static int rowsUpTo(int total, int page) {
        return total > 0 ? total : page;
    }
}
