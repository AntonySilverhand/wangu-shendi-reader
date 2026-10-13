package org.wanshu.reader;

import java.util.concurrent.CountDownLatch;
import org.wanshu.reader.data.repository.TocLoadListener;

public final class TocLoadTestListener implements TocLoadListener {
    final CountDownLatch done = new CountDownLatch(1);
    volatile int loaded, total;
    volatile String error;
    @Override public void onProgress(int loaded, int total, boolean finished, String error) {
        this.loaded = loaded; this.total = total; this.error = error;
        if (finished) done.countDown();
    }
}
