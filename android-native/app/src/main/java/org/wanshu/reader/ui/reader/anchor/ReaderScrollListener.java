package org.wanshu.reader.ui.reader.anchor;

import androidx.recyclerview.widget.RecyclerView;
import org.wanshu.reader.core.model.ReadingAnchor;

public class ReaderScrollListener extends RecyclerView.OnScrollListener {
    private final ReaderAnchorSampler sampler;
    private final AnchorSampleCallback callback;
    private long lastSaveTime = 0L;
    private ReadingAnchor candidateAnchor = null;

    public ReaderScrollListener(ReaderAnchorSampler sampler, AnchorSampleCallback callback) {
        this.sampler = sampler;
        this.callback = callback;
    }

    @Override
    public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
        if (dy == 0) {
            return;
        }

        candidateAnchor = sampler.sampleAnchor();
        long now = System.currentTimeMillis();
        // Continuous scroll throttle: at most once per 1000ms
        if (now - lastSaveTime >= 1000L && candidateAnchor != null) {
            lastSaveTime = now;
            if (callback != null) {
                callback.onAnchorSampled(candidateAnchor);
            }
        }
    }

    @Override
    public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
            candidateAnchor = sampler.sampleAnchor();
            lastSaveTime = System.currentTimeMillis();
            if (candidateAnchor != null && callback != null) {
                callback.onAnchorSampled(candidateAnchor);
            }
        }
    }

    public ReadingAnchor getCandidateAnchor() {
        return candidateAnchor;
    }
}
