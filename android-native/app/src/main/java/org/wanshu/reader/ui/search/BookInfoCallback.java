package org.wanshu.reader.ui.search;

import org.wanshu.reader.core.model.BookInfo;
import org.wanshu.reader.core.queue.SchedulerCallback;

/** Scheduler callback of the detail-page request. */
public class BookInfoCallback implements SchedulerCallback<BookInfo> {
    private final BookDetailDialog dialog;
    private final long generation;

    public BookInfoCallback(BookDetailDialog dialog, long generation) {
        this.dialog = dialog;
        this.generation = generation;
    }

    @Override
    public void onSuccess(BookInfo result) {
        dialog.postToUi(new BookInfoResultRunnable(dialog, generation, result, null));
    }

    @Override
    public void onError(Throwable error) {
        dialog.postToUi(new BookInfoResultRunnable(dialog, generation, null, error));
    }

    @Override
    public void onCancelled() {
        // dialog closed
    }
}
