package org.wanshu.reader.ui.reader;

import org.wanshu.reader.core.model.ReadingAnchor;
import org.wanshu.reader.data.repository.DataCallback;

public final class ReaderOpenProgressCallback implements DataCallback<ReadingAnchor>, Runnable {
    private final ReaderController controller;
    private final long generation;
    private ReadingAnchor anchor;
    public ReaderOpenProgressCallback(ReaderController controller, long generation) {
        this.controller = controller; this.generation = generation;
    }
    @Override public void onSuccess(ReadingAnchor value) { anchor = value; controller.postToUi(this); }
    @Override public void onError(Throwable error) { controller.postToUi(this); }
    @Override public void run() { controller.beginObservation(generation, anchor); }
}
