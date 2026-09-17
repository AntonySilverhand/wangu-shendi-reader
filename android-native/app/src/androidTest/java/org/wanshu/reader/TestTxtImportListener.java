package org.wanshu.reader;

import java.util.concurrent.CountDownLatch;
import org.wanshu.reader.data.txt.TxtImportListener;

public class TestTxtImportListener implements TxtImportListener {
    private final CountDownLatch latch;
    private String bookId;
    private int chaptersCount;
    private long totalBytes;
    private Throwable error;

    public TestTxtImportListener(CountDownLatch latch) {
        this.latch = latch;
    }

    @Override
    public void onProgress(int chaptersCount, long totalBytes) {
        this.chaptersCount = chaptersCount;
        this.totalBytes = totalBytes;
    }

    @Override
    public void onSuccess(String bookId, int chaptersCount, long totalBytes) {
        this.bookId = bookId;
        this.chaptersCount = chaptersCount;
        this.totalBytes = totalBytes;
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

    public String getBookId() {
        return bookId;
    }

    public int getChaptersCount() {
        return chaptersCount;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public Throwable getError() {
        return error;
    }
}
