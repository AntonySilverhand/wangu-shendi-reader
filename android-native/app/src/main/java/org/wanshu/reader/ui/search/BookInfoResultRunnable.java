package org.wanshu.reader.ui.search;

import org.wanshu.reader.core.model.BookInfo;

/** Main thread: hands the loaded book info to the detail dialog. */
public class BookInfoResultRunnable implements Runnable {
    private final BookDetailDialog dialog;
    private final long generation;
    private final BookInfo info;
    private final Throwable error;

    public BookInfoResultRunnable(BookDetailDialog dialog, long generation, BookInfo info, Throwable error) {
        this.dialog = dialog;
        this.generation = generation;
        this.info = info;
        this.error = error;
    }

    @Override
    public void run() {
        dialog.applyInfo(generation, info, error);
    }
}
