package org.wanshu.reader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.File;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wanshu.reader.core.model.ReadingAnchor;
import org.wanshu.reader.data.personal.PersonalDatabase;
import org.wanshu.reader.data.personal.entity.BookmarkEntity;
import org.wanshu.reader.data.personal.entity.ReadingHistoryEntity;
import org.wanshu.reader.data.personal.entity.ReadingProgressEntity;
import org.wanshu.reader.data.repository.PersonalRepository;

@RunWith(AndroidJUnit4.class)
public class PersonalDurabilityTest {
    private Context context;
    private File dbFile;
    private PersonalDatabase db;
    private PersonalRepository repository;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        dbFile = new File(context.getFilesDir(), "test-personal.db");
        if (dbFile.exists()) {
            dbFile.delete();
        }
        db = Room.databaseBuilder(context, PersonalDatabase.class, dbFile.getAbsolutePath())
                .allowMainThreadQueries()
                .build();
        repository = new PersonalRepository(db, Executors.newSingleThreadExecutor());
    }

    @After
    public void tearDown() {
        if (db != null) {
            db.close();
        }
        if (dbFile != null && dbFile.exists()) {
            dbFile.delete();
        }
    }

    @Test
    public void testReadingProgressAndReopen() throws Exception {
        ReadingAnchor anchor = new ReadingAnchor("36780", "50", 12, 140, 1000L);
        CountDownLatch latch = new CountDownLatch(1);
        repository.saveReadingProgress(anchor, new ContentTestCompletionCallback(latch));
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        // Reopen DB
        db.close();
        db = Room.databaseBuilder(context, PersonalDatabase.class, dbFile.getAbsolutePath())
                .allowMainThreadQueries()
                .build();

        ReadingProgressEntity progress = db.readingProgressDao().getProgress("36780");
        assertNotNull(progress);
        assertEquals("50", progress.chapterId);
        assertEquals(12, progress.paragraphIndex);
        assertEquals(140, progress.offsetUtf16);
    }

    @Test
    public void testHistoryCap30() throws Exception {
        for (int i = 1; i <= 35; i++) {
            CountDownLatch latch = new CountDownLatch(1);
            repository.recordReadingHistory("36780", String.valueOf(i), "第 " + i + " 章", new ContentTestCompletionCallback(latch));
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        }

        List<ReadingHistoryEntity> history = db.readingHistoryDao().getHistoryForBook("36780");
        assertEquals(30, history.size());
        assertEquals("35", history.get(0).chapterId);
    }
}
