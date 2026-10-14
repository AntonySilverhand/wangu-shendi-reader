package org.wanshu.reader.ui.search;

/** Main thread: updates the detail dialog's shelf button and message. */
public class BookDetailShelfStateRunnable implements Runnable {
    private final BookDetailDialog dialog;
    private final boolean onShelf;
    private final String message;

    public BookDetailShelfStateRunnable(BookDetailDialog dialog, boolean onShelf, String message) {
        this.dialog = dialog;
        this.onShelf = onShelf;
        this.message = message;
    }

    @Override
    public void run() {
        dialog.applyShelfState(onShelf, message);
    }
}
