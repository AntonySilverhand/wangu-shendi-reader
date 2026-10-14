package org.wanshu.reader.ui.search;

import java.util.List;
import org.wanshu.reader.core.model.BookSummary;

/** Main thread: hands a finished search to the dialog. */
public class BookSearchResultRunnable implements Runnable {
    private final BookSearchDialog dialog;
    private final long generation;
    private final List<BookSummary> results;
    private final Throwable error;

    public BookSearchResultRunnable(BookSearchDialog dialog, long generation, List<BookSummary> results, Throwable error) {
        this.dialog = dialog;
        this.generation = generation;
        this.results = results;
        this.error = error;
    }

    @Override
    public void run() {
        dialog.applyResult(generation, results, error);
    }
}
