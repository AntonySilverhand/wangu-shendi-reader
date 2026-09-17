package org.wanshu.reader;

import java.util.concurrent.CountDownLatch;
import org.wanshu.reader.download.DownloadProgress;
import org.wanshu.reader.download.DownloadProgressListener;

public class TestDownloadProgressListener implements DownloadProgressListener {
    private final CountDownLatch latch;
    private volatile DownloadProgress lastProgress;

    public TestDownloadProgressListener(CountDownLatch latch) {
        this.latch = latch;
    }

    @Override
    public void onDownloadProgress(DownloadProgress progress) {
        this.lastProgress = progress;
        if (latch != null) {
            latch.countDown();
        }
    }

    public DownloadProgress getLastProgress() {
        return lastProgress;
    }
}
