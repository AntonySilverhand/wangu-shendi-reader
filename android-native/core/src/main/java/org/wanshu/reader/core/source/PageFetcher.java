package org.wanshu.reader.core.source;

public interface PageFetcher {
    PageFetchOutcome fetch(String chapterId, int pageIndex);
}
