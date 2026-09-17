package org.wanshu.reader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.File;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wanshu.reader.core.model.ChapterResult;
import org.wanshu.reader.core.text.TextBlockBuilder;
import org.wanshu.reader.data.content.ContentDatabase;
import org.wanshu.reader.data.content.StorageStats;
import org.wanshu.reader.data.content.entity.BookEntity;
import org.wanshu.reader.data.content.entity.ChapterEntity;
import org.wanshu.reader.data.repository.CompletionCallback;
import org.wanshu.reader.data.repository.ContentRepository;
import org.wanshu.reader.data.repository.DataCallback;

@RunWith(AndroidJUnit4.class)
public class ContentDurabilityTest {
    private Context context;
    private File dbFile;
    private ContentDatabase db;
    private ContentRepository repository;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        dbFile = new File(context.getFilesDir(), "test-content.db");
        if (dbFile.exists()) {
            dbFile.delete();
        }
        db = Room.databaseBuilder(context, ContentDatabase.class, dbFile.getAbsolutePath())
                .allowMainThreadQueries()
                .build();
        repository = new ContentRepository(db, Executors.newSingleThreadExecutor(), new TextBlockBuilder(2000));
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
    public void testSaveChapterAndReopen() throws Exception {
        ChapterResult chapter = new ChapterResult(
                "36780",
                "100",
                "第一百章 大道无垠",
                Arrays.asList("第一段：神光万道，瑞彩千条。", "第二段：张若尘仗剑而立，战意冲天。"),
                "99",
                "101",
                1,
                32,
                true,
                Arrays.<Integer>asList()
        );

        final CountDownLatch latch = new CountDownLatch(1);
        repository.saveChapter(chapter, "REMOTE", new ContentTestCompletionCallback(latch));
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        // Reopen database from disk file
        db.close();
        db = Room.databaseBuilder(context, ContentDatabase.class, dbFile.getAbsolutePath())
                .allowMainThreadQueries()
                .build();
        repository = new ContentRepository(db, Executors.newSingleThreadExecutor(), new TextBlockBuilder(2000));

        ChapterEntity entity = db.chapterDao().getChapter("36780", "100");
        assertNotNull(entity);
        assertEquals("第一百章 大道无垠", entity.title);
        assertTrue(entity.isComplete);
        assertEquals(32, entity.charCount);
        assertEquals("99", entity.prevChapterId);
        assertEquals("101", entity.nextChapterId);
    }

    @Test
    public void testShorterChapterRejected() throws Exception {
        ChapterResult longChapter = new ChapterResult(
                "36780",
                "200",
                "长章节",
                Arrays.asList("一段很长的正文...", "二段很长的正文..."),
                null,
                null,
                1,
                100,
                true,
                Arrays.<Integer>asList()
        );

        CountDownLatch latch1 = new CountDownLatch(1);
        repository.saveChapter(longChapter, "REMOTE", new ContentTestCompletionCallback(latch1));
        assertTrue(latch1.await(5, TimeUnit.SECONDS));

        ChapterResult shortChapter = new ChapterResult(
                "36780",
                "200",
                "短章节",
                Arrays.asList("被截断的正文"),
                null,
                null,
                1,
                6,
                false,
                Arrays.<Integer>asList(1)
        );

        CountDownLatch latch2 = new CountDownLatch(1);
        repository.saveChapter(shortChapter, "REMOTE", new ContentTestCompletionCallback(latch2));
        assertTrue(latch2.await(5, TimeUnit.SECONDS));

        ChapterEntity current = db.chapterDao().getChapter("36780", "200");
        assertNotNull(current);
        assertEquals("长章节", current.title);
        assertEquals(100, current.charCount);
        assertTrue(current.isComplete);
    }

    @Test
    public void testClearRemoteCacheDoesNotTouchLocalBook() throws Exception {
        // Insert remote chapter
        ChapterResult remoteChapter = new ChapterResult(
                "36780",
                "300",
                "远程章节",
                Arrays.asList("远程段落"),
                null,
                null,
                1,
                4,
                true,
                Arrays.<Integer>asList()
        );
        CountDownLatch latch1 = new CountDownLatch(1);
        repository.saveChapter(remoteChapter, "REMOTE", new ContentTestCompletionCallback(latch1));
        assertTrue(latch1.await(5, TimeUnit.SECONDS));

        // Insert local book and chapter
        BookEntity localBook = new BookEntity();
        localBook.bookId = "local-novel-1";
        localBook.sourceType = "LOCAL_TXT";
        localBook.title = "本地导入小说";
        db.bookDao().insertBook(localBook);

        ChapterResult localChapter = new ChapterResult(
                "local-novel-1",
                "1",
                "本地第一章",
                Arrays.asList("本地正文内容"),
                null,
                null,
                1,
                6,
                true,
                Arrays.<Integer>asList()
        );
        CountDownLatch latch2 = new CountDownLatch(1);
        repository.saveChapter(localChapter, "LOCAL", new ContentTestCompletionCallback(latch2));
        assertTrue(latch2.await(5, TimeUnit.SECONDS));

        // Clear remote cache for 36780
        CountDownLatch latch3 = new CountDownLatch(1);
        repository.clearRemoteCache("36780", new ContentTestCompletionCallback(latch3));
        assertTrue(latch3.await(5, TimeUnit.SECONDS));

        // Remote chapters are gone
        ChapterEntity remoteAfter = db.chapterDao().getChapter("36780", "300");
        assertEquals(null, remoteAfter);

        // Local book and chapters remain completely intact!
        BookEntity localBookAfter = db.bookDao().getBook("local-novel-1");
        assertNotNull(localBookAfter);
        assertEquals("本地导入小说", localBookAfter.title);

        ChapterEntity localChapterAfter = db.chapterDao().getChapter("local-novel-1", "1");
        assertNotNull(localChapterAfter);
        assertEquals("本地第一章", localChapterAfter.title);
    }
}
