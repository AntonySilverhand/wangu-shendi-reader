package org.wanshu.reader;

import java.util.concurrent.CountDownLatch;
import org.wanshu.reader.data.repository.CompletionCallback;

public class ContentTestCompletionCallback implements CompletionCallback {
    private final CountDownLatch latch;

    public ContentTestCompletionCallback(CountDownLatch latch) {
        this.latch = latch;
    }

    @Override
    public void onSuccess() {
        latch.countDown();
    }

    @Override
    public void onError(Throwable error) {
        latch.countDown();
    }
}
