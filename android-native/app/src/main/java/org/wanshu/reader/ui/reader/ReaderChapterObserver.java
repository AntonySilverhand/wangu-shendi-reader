package org.wanshu.reader.ui.reader;

import org.wanshu.reader.core.model.ChapterResult;
import org.wanshu.reader.data.repository.ReaderObserver;

public class ReaderChapterObserver implements ReaderObserver {
    private final ReaderController controller;
    private final long generation;

    public ReaderChapterObserver(ReaderController controller, long generation) {
        this.controller = controller;
        this.generation = generation;
    }

    @Override
    public void onCachedLoaded(ChapterResult cached) {
        controller.onChapterLoaded(cached, generation, false);
    }

    @Override
    public void onCompleteLoaded(ChapterResult complete) {
        controller.onChapterLoaded(complete, generation, true);
    }

    @Override
    public void onError(Throwable error) {
        controller.onChapterLoadFailed(error, generation);
    }
}
