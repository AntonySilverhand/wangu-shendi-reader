package org.wanshu.reader.ui.search;

import java.util.List;
import org.wanshu.reader.core.model.BookSummary;
import org.wanshu.reader.core.queue.SchedulerCallback;

/** Scheduler callback of one search; carries the generation it was started with. */
public class BookSearchCallback implements SchedulerCallback<List<BookSummary>> {
    private final BookSearchDialog dialog;
    private final long generation;

    public BookSearchCallback(BookSearchDialog dialog, long generation) {
        this.dialog = dialog;
        this.generation = generation;
    }

    @Override
    public void onSuccess(List<BookSummary> result) {
        dialog.postToUi(new BookSearchResultRunnable(dialog, generation, result, null));
    }

    @Override
    public void onError(Throwable error) {
        dialog.postToUi(new BookSearchResultRunnable(dialog, generation, null, error));
    }

    @Override
    public void onCancelled() {
        // replaced by a newer search or the dialog was closed; nothing to show
    }
}
