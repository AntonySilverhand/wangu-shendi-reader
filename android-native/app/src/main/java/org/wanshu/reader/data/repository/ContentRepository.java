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
    private NativeTocLoader tocLoader;

    public void setTocLoader(NativeTocLoader loader) { tocLoader = loader; }
    public NativeTocLoader getTocLoader() { return tocLoader; }

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

    /** Adds the default book once (see SeedDefaultBookRunnable); a removed default book stays removed. */
    public void seedDefaultBook(android.content.SharedPreferences prefs, CompletionCallback callback) {
        dbExecutor.execute(new SeedDefaultBookRunnable(db, prefs, callback));
    }

    /** Title of a chapter from the saved TOC or cached chapter; empty when neither is known. */
    public void getChapterTitle(String bookId, String chapterId, DataCallback<String> callback) {
        dbExecutor.execute(new GetChapterTitleRunnable(db, bookId, chapterId, callback));
    }

    public void getTocEntries(String bookId, DataCallback<java.util.List<org.wanshu.reader.data.content.entity.TocEntryEntity>> callback) {
        dbExecutor.execute(new GetTocEntriesRunnable(db, bookId, callback));
    }

    public void getChapterCacheStatus(String bookId, DataCallback<org.wanshu.reader.data.content.ChapterCacheStatus> callback) {
        dbExecutor.execute(new GetChapterCacheStatusRunnable(db, bookId, callback));
    }
}
