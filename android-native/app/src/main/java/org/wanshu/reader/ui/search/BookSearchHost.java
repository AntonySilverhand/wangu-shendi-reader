package org.wanshu.reader.ui.search;

import java.util.List;
import org.wanshu.reader.data.content.entity.BookEntity;
import org.wanshu.reader.data.repository.CompletionCallback;
import org.wanshu.reader.data.repository.DataCallback;

/** What the search and detail dialogs need from the shelf. Callbacks arrive on a background executor. */
public interface BookSearchHost {
    /** Puts an online book on the shelf (or refreshes its title and author when it is already there). */
    void addBook(String bookId, String title, String author, CompletionCallback callback);

    /** Adds the book to the shelf and opens its first chapter (firstChapterId may be null). */
    void startReading(String bookId, String title, String author, String firstChapterId);

    /** All books currently on the shelf. */
    void loadShelfBooks(DataCallback<List<BookEntity>> callback);
}
