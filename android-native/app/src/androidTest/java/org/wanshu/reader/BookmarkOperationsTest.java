package org.wanshu.reader;

import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wanshu.reader.core.model.ReadingAnchor;
import org.wanshu.reader.data.personal.PersonalDatabase;
import org.wanshu.reader.data.personal.entity.ReadingProgressEntity;
import org.wanshu.reader.data.repository.AddBookmarkRunnable;
import org.wanshu.reader.data.repository.ClearBookmarksRunnable;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class BookmarkOperationsTest {
    private PersonalDatabase db;
    @Before public void createDb() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().getTargetContext(), PersonalDatabase.class).build();
    }
    @After public void closeDb() { db.close(); }

    @Test public void nearbyPositionsDeduplicateButOtherLocationsAndBooksRemainDistinct() {
        add("36780", 20); add("36780", 25); add("36780", 20);
        assertEquals(1, db.bookmarkDao().getBookmarks("36780").size());
        add("36780", 1000); add("local-other", 20);
        assertEquals(2, db.bookmarkDao().getBookmarks("36780").size());
        assertEquals(1, db.bookmarkDao().getBookmarks("local-other").size());
        assertEquals(20, db.bookmarkDao().findNearby("36780", "1", 2, 25).offsetUtf16);
    }

    @Test public void clearingOneBookPreservesOtherBookmarksAndReadingProgress() {
        add("36780", 20); add("local-other", 20);
        ReadingProgressEntity progress = new ReadingProgressEntity();
        progress.bookId = "36780"; progress.chapterId = "1"; progress.offsetUtf16 = 50;
        db.readingProgressDao().saveProgress(progress);
        new ClearBookmarksRunnable(db, "36780", null).run();
        assertTrue(db.bookmarkDao().getBookmarks("36780").isEmpty());
        assertEquals(1, db.bookmarkDao().getBookmarks("local-other").size());
        assertEquals(50, db.readingProgressDao().getProgress("36780").offsetUtf16);
    }

    private void add(String bookId, int offset) {
        new AddBookmarkRunnable(db, new ReadingAnchor(bookId, "1", 2, offset, 1), "片段😀", null).run();
    }
}
