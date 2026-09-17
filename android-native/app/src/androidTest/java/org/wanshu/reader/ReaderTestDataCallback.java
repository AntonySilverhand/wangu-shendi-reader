package org.wanshu.reader;

import java.util.concurrent.CountDownLatch;
import org.wanshu.reader.core.model.ChapterResult;
import org.wanshu.reader.data.repository.DataCallback;

public class ReaderTestDataCallback implements DataCallback<ChapterResult> {
    private final CountDownLatch latch;
    private ChapterResult result;
    private Throwable error;

    public ReaderTestDataCallback(CountDownLatch latch) {
        this.latch = latch;
    }

    @Override
    public void onSuccess(ChapterResult result) {
        this.result = result;
        latch.countDown();
    }

    @Override
    public void onError(Throwable error) {
        this.error = error;
        latch.countDown();
    }

    public ChapterResult getResult() {
        return result;
    }

    public Throwable getError() {
        return error;
    }
}
