package org.wanshu.reader;

import java.util.concurrent.CountDownLatch;
import org.wanshu.reader.core.model.ReadingAnchor;
import org.wanshu.reader.data.repository.DataCallback;

public class AnchorTestDataCallback implements DataCallback<ReadingAnchor> {
    private final CountDownLatch latch;
    private ReadingAnchor anchor;
    private Throwable error;

    public AnchorTestDataCallback(CountDownLatch latch) {
        this.latch = latch;
    }

    @Override
    public void onSuccess(ReadingAnchor anchor) {
        this.anchor = anchor;
        if (latch != null) {
            latch.countDown();
        }
    }

    @Override
    public void onError(Throwable error) {
        this.error = error;
        if (latch != null) {
            latch.countDown();
        }
    }

    public ReadingAnchor getAnchor() {
        return anchor;
    }

    public Throwable getError() {
        return error;
    }
}
