package org.wanshu.reader.ui.reader.anchor;

import androidx.recyclerview.widget.RecyclerView;
import org.wanshu.reader.core.model.ReadingAnchor;

/** Samples the reading anchor only after scrolling stops; no per-frame layout reads. */
public class ReaderScrollListener extends RecyclerView.OnScrollListener {
    private final ReaderAnchorSampler sampler;
    private final AnchorSampleCallback callback;

    public ReaderScrollListener(ReaderAnchorSampler sampler, AnchorSampleCallback callback) {
        this.sampler = sampler;
        this.callback = callback;
    }

    @Override
    public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
        if (newState != RecyclerView.SCROLL_STATE_IDLE || callback == null) return;
        ReadingAnchor anchor = sampler.sampleAnchor();
        if (anchor != null) callback.onAnchorSampled(anchor);
    }
}
