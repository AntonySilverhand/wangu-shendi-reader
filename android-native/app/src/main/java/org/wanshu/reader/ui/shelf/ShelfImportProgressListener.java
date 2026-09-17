package org.wanshu.reader.ui.shelf;

import org.wanshu.reader.data.txt.TxtImportListener;

public class ShelfImportProgressListener implements TxtImportListener {
    private final ShelfController controller;

    public ShelfImportProgressListener(ShelfController controller) {
        this.controller = controller;
    }

    @Override
    public void onProgress(int chaptersCount, long totalBytes) {
        controller.onImportProgress(chaptersCount, totalBytes);
    }

    @Override
    public void onSuccess(String bookId, int chaptersCount, long totalBytes) {
        controller.onImportSuccess(bookId, chaptersCount, totalBytes);
    }

    @Override
    public void onError(Throwable error) {
        controller.onImportError(error);
    }
}
