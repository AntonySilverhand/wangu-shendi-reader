package org.wanshu.reader.ui.toc;

import java.util.ArrayList;
import java.util.List;
import org.wanshu.reader.core.model.TocEntry;
import org.wanshu.reader.core.net.CancellationToken;
import org.wanshu.reader.core.toc.TocSearchDeps;
import org.wanshu.reader.core.toc.TocSearchResult;
import org.wanshu.reader.data.content.entity.TocEntryEntity;

public class RoomTocSearchDeps implements TocSearchDeps {
    private final String bookId;
    private final List<TocEntryEntity> loadedEntries;
    private final int totalPagesCount;
    private final int loadedPagesCount;

    public RoomTocSearchDeps(
            String bookId,
            List<TocEntryEntity> loadedEntries,
            int totalPagesCount,
            int loadedPagesCount
    ) {
        this.bookId = bookId != null ? bookId : "";
        this.loadedEntries = loadedEntries != null ? loadedEntries : new ArrayList<TocEntryEntity>();
        this.totalPagesCount = totalPagesCount > 0 ? totalPagesCount : (this.bookId.startsWith("local-") ? 1 : 44);
        this.loadedPagesCount = Math.max(0, loadedPagesCount);
    }

    @Override
    public List<TocSearchResult> searchLoaded(String query, int limit) {
        List<TocSearchResult> out = new ArrayList<TocSearchResult>();
        if (query == null || query.trim().isEmpty()) {
            return out;
        }

        String q = query.trim();
        Integer numeric = null;
        try {
            numeric = Integer.parseInt(q);
        } catch (NumberFormatException ignored) {}

        String lower = q.toLowerCase(java.util.Locale.ROOT);

        for (int i = 0; i < loadedEntries.size() && out.size() < limit; i++) {
            TocEntryEntity e = loadedEntries.get(i);
            boolean titleMatch = e.title.toLowerCase(java.util.Locale.ROOT).contains(lower);
            Integer chapterNumber = org.wanshu.reader.core.source.TitleNormalizer.normalizeTitle(e.title).getNumber();
            boolean numMatch = e.chapterId.equals(q) || (numeric != null && numeric.equals(chapterNumber));
            if (titleMatch || numMatch) {
                TocEntry coreEntry = new TocEntry(e.bookId, e.chapterId, e.title, e.orderKey, e.isExtra, e.sourcePageIndex);
                out.add(new TocSearchResult(i, coreEntry));
            }
        }
        return out;
    }

    @Override
    public int countLoadable() {
        // This adapter searches a DB snapshot. Network loading is explicit via NativeTocLoader,
        // never pretend a no-op loadRange advanced a progressive search.
        return 0;
    }

    @Override
    public Integer nextLoadablePage() {
        return null;
    }

    @Override
    public boolean isComplete() {
        if (bookId.startsWith("local-")) {
            return true;
        }
        return loadedPagesCount >= totalPagesCount;
    }

    @Override
    public int failedPageCount() {
        return 0;
    }

    @Override
    public int loadedPageCount() {
        return loadedPagesCount;
    }

    @Override
    public int totalPages() {
        return totalPagesCount;
    }

    @Override
    public void loadRange(int from, int to, CancellationToken token) throws Exception {
        if (!bookId.startsWith("local-")) throw new IllegalStateException("请先加载目录，再搜索已保存的章节");
    }

    @Override
    public Integer estimatePageForNumber(int number) {
        return null; // Snapshot search does not issue implicit network probes.
    }

    @Override
    public void retryFailedPages(CancellationToken token) throws Exception {
    }
}
