package org.wanshu.reader.core.toc;

import java.util.List;
import org.wanshu.reader.core.net.CancellationToken;

public interface TocSearchDeps {
    List<TocSearchResult> searchLoaded(String query, int limit);
    int countLoadable();
    Integer nextLoadablePage();
    boolean isComplete();
    int failedPageCount();
    int loadedPageCount();
    int totalPages();
    void loadRange(int from, int to, CancellationToken token) throws Exception;
    Integer estimatePageForNumber(int number);
    void retryFailedPages(CancellationToken token) throws Exception;
}
