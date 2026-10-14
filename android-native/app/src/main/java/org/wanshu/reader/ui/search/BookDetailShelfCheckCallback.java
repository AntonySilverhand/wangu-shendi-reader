package org.wanshu.reader.ui.search;

import java.util.List;
import org.wanshu.reader.data.content.entity.BookEntity;
import org.wanshu.reader.data.repository.DataCallback;

/** Finds out whether the book shown in the detail dialog is already on the shelf. */
public class BookDetailShelfCheckCallback implements DataCallback<List<BookEntity>> {
    private final BookDetailDialog dialog;
    private final String bookId;

    public BookDetailShelfCheckCallback(BookDetailDialog dialog, String bookId) {
        this.dialog = dialog;
        this.bookId = bookId;
    }

    @Override
    public void onSuccess(List<BookEntity> books) {
        boolean found = false;
        if (books != null) {
            for (int i = 0; i < books.size(); i++) {
                if (bookId.equals(books.get(i).bookId)) {
                    found = true;
                    break;
                }
            }
        }
        dialog.postToUi(new BookDetailShelfStateRunnable(dialog, found, null));
    }

    @Override
    public void onError(Throwable error) {
        // unknown: leave the button usable, adding is idempotent
    }
}
