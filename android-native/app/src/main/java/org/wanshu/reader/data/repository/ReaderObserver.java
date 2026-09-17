package org.wanshu.reader.data.repository;

import org.wanshu.reader.core.model.ChapterResult;

public interface ReaderObserver {
    void onCachedLoaded(ChapterResult cached);
    void onCompleteLoaded(ChapterResult complete);
    void onError(Throwable error);
}
