package org.wanshu.reader.data.repository;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import org.wanshu.reader.core.model.ReadingAnchor;
import org.wanshu.reader.data.personal.PersonalDatabase;
import org.wanshu.reader.data.personal.entity.BookmarkEntity;
import org.wanshu.reader.data.personal.entity.LastRouteEntity;
import org.wanshu.reader.data.personal.entity.SettingsEntity;

public class PersonalRepository {
    private final PersonalDatabase db;
    private final Executor dbExecutor;
    private final AtomicLong sequenceGenerator = new AtomicLong(System.currentTimeMillis());

    public PersonalRepository(PersonalDatabase db, Executor dbExecutor) {
        this.db = db;
        this.dbExecutor = dbExecutor;
    }

    public PersonalDatabase getDatabase() {
        return db;
    }

    public void saveReadingProgress(ReadingAnchor anchor, CompletionCallback callback) {
        long seq = sequenceGenerator.incrementAndGet();
        dbExecutor.execute(new SaveProgressRunnable(db, anchor, seq, callback));
    }

    public void getReadingProgress(String bookId, DataCallback<ReadingAnchor> callback) {
        dbExecutor.execute(new GetProgressRunnable(db, bookId, callback));
    }

    public void addBookmark(ReadingAnchor anchor, String snippet, DataCallback<BookmarkEntity> callback) {
        dbExecutor.execute(new AddBookmarkRunnable(db, anchor, snippet, callback));
    }

    public void deleteBookmark(String id, CompletionCallback callback) {
        dbExecutor.execute(new DeleteBookmarkRunnable(db, id, callback));
    }

    public void getBookmarks(String bookId, DataCallback<List<BookmarkEntity>> callback) {
        dbExecutor.execute(new GetBookmarksRunnable(db, bookId, callback));
    }

    public void recordReadingHistory(String bookId, String chapterId, String title, CompletionCallback callback) {
        dbExecutor.execute(new RecordHistoryRunnable(db, bookId, chapterId, title, System.currentTimeMillis(), callback));
    }

    public void getSettings(DataCallback<SettingsEntity> callback) {
        dbExecutor.execute(new GetSettingsRunnable(db, callback));
    }

    public void saveSettings(SettingsEntity settings, CompletionCallback callback) {
        dbExecutor.execute(new SaveSettingsRunnable(db, settings, callback));
    }

    public void saveLastRoute(LastRouteEntity route, CompletionCallback callback) {
        dbExecutor.execute(new SaveLastRouteRunnable(db, route, callback));
    }

    public void getLastRoute(DataCallback<LastRouteEntity> callback) {
        dbExecutor.execute(new GetLastRouteRunnable(db, callback));
    }
}
