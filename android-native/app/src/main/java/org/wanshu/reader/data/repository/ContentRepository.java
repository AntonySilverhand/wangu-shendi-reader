package org.wanshu.reader.data.repository;

import java.util.concurrent.Executor;
import org.wanshu.reader.core.model.ChapterResult;
import org.wanshu.reader.core.text.TextBlockBuilder;
import org.wanshu.reader.data.content.ContentDatabase;
import org.wanshu.reader.data.content.StorageStats;

public class ContentRepository {
    private final ContentDatabase db;
    private final Executor dbExecutor;
    private final TextBlockBuilder textBlockBuilder;

    public ContentRepository(
            ContentDatabase db,
            Executor dbExecutor,
            TextBlockBuilder textBlockBuilder
    ) {
        this.db = db;
        this.dbExecutor = dbExecutor;
        this.textBlockBuilder = textBlockBuilder != null ? textBlockBuilder : new TextBlockBuilder();
    }

    public ContentDatabase getDatabase() {
        return db;
    }

    public void saveChapter(ChapterResult result, String source, CompletionCallback callback) {
        dbExecutor.execute(new SaveChapterRunnable(db, result, source, textBlockBuilder, callback));
    }

    public void getChapter(String bookId, String chapterId, DataCallback<ChapterResult> callback) {
        dbExecutor.execute(new GetChapterRunnable(db, bookId, chapterId, callback));
    }

    public void clearRemoteCache(String bookId, CompletionCallback callback) {
        dbExecutor.execute(new ClearRemoteCacheRunnable(db, bookId, callback));
    }

    public void deleteLocalBook(String bookId, CompletionCallback callback) {
        dbExecutor.execute(new DeleteLocalBookRunnable(db, bookId, callback));
    }

    public void getStorageStats(String bookId, DataCallback<StorageStats> callback) {
        dbExecutor.execute(new GetStorageStatsRunnable(db, bookId, callback));
    }

    public void getAllBooks(DataCallback<java.util.List<org.wanshu.reader.data.content.entity.BookEntity>> callback) {
        dbExecutor.execute(new GetAllBooksRunnable(db, callback));
    }

    public void ensureOnlineBook(String bookId, String title, String author, CompletionCallback callback) {
        dbExecutor.execute(new EnsureOnlineBookRunnable(db, bookId, title, author, callback));
    }

    public void getTocEntries(String bookId, DataCallback<java.util.List<org.wanshu.reader.data.content.entity.TocEntryEntity>> callback) {
        dbExecutor.execute(new GetTocEntriesRunnable(db, bookId, callback));
    }
}
