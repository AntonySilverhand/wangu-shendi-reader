package org.wanshu.reader.ui.reader;

import org.wanshu.reader.core.model.ReadingAnchor;
import org.wanshu.reader.ui.reader.anchor.AnchorSampleCallback;

public class ReaderAnchorCallback implements AnchorSampleCallback {
    private final ReaderController controller;

    public ReaderAnchorCallback(ReaderController controller) {
        this.controller = controller;
    }

    @Override
    public void onAnchorSampled(ReadingAnchor anchor) {
        controller.onAnchorSampled(anchor);
    }
}
