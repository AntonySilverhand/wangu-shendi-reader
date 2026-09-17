package org.wanshu.reader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wanshu.reader.core.model.ChapterResult;
import org.wanshu.reader.core.net.FakeHttpConnectionFactory;
import org.wanshu.reader.core.net.SourceHttpClient;
import org.wanshu.reader.core.queue.RequestPriority;
import org.wanshu.reader.core.queue.RequestScheduler;
import org.wanshu.reader.core.text.TextBlockBuilder;
import org.wanshu.reader.core.util.JavaBase64Decoder;
import org.wanshu.reader.data.content.ContentDatabase;
import org.wanshu.reader.data.content.entity.BookEntity;
import org.wanshu.reader.data.repository.ContentRepository;
import org.wanshu.reader.data.repository.ReaderRepository;

@RunWith(AndroidJUnit4.class)
public class ReaderRepositoryTest {

    private Context context;
    private File dbFile;
    private ContentDatabase db;
    private ContentRepository contentRepository;
    private RequestScheduler scheduler;
    private FakeHttpConnectionFactory connectionFactory;
    private SourceHttpClient httpClient;
    private ReaderRepository readerRepository;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        dbFile = new File(context.getFilesDir(), "test-reader-content.db");
        if (dbFile.exists()) {
            dbFile.delete();
        }
        db = Room.databaseBuilder(context, ContentDatabase.class, dbFile.getAbsolutePath())
                .allowMainThreadQueries()
                .build();

        BookEntity book = new BookEntity();
        book.bookId = "36780";
        book.title = "万古神帝";
        book.sourceType = "ONLINE";
        book.importStatus = "READY";
        book.createdAt = System.currentTimeMillis();
        book.updatedAt = System.currentTimeMillis();
        db.bookDao().insertBook(book);

        contentRepository = new ContentRepository(db, Executors.newSingleThreadExecutor(), new TextBlockBuilder(2000));
        scheduler = new RequestScheduler();
        connectionFactory = new FakeHttpConnectionFactory();
        httpClient = new SourceHttpClient(connectionFactory, false);
        readerRepository = new ReaderRepository(contentRepository, scheduler, httpClient, new JavaBase64Decoder());
    }

    @After
    public void tearDown() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
        if (db != null) {
            db.close();
        }
        if (dbFile != null && dbFile.exists()) {
            dbFile.delete();
        }
    }

    @Test
    public void testReadCachedReturnsDatabaseContent() throws Exception {
        ChapterResult chapter = new ChapterResult(
                "36780",
                "1001",
                "第一章",
                Arrays.asList("第一段正文", "第二段正文"),
                null,
                "1002",
                1,
                12,
                true,
                Collections.<Integer>emptyList()
        );

        CountDownLatch saveLatch = new CountDownLatch(1);
        contentRepository.saveChapter(chapter, "REMOTE", new ContentTestCompletionCallback(saveLatch));
        assertTrue(saveLatch.await(5, TimeUnit.SECONDS));

        CountDownLatch readLatch = new CountDownLatch(1);
        ReaderTestDataCallback readCb = new ReaderTestDataCallback(readLatch);
        readerRepository.readCached("36780", "1001", readCb);
        assertTrue(readLatch.await(5, TimeUnit.SECONDS));

        assertNotNull(readCb.getResult());
        assertEquals("第一章", readCb.getResult().getTitle());
        assertEquals(2, readCb.getResult().getParagraphs().size());
        assertEquals(0, connectionFactory.getRequestedUrls().size()); // Zero network
    }

    @Test
    public void testEnsureCompleteReturnsCachedWhenAlreadyComplete() throws Exception {
        ChapterResult chapter = new ChapterResult(
                "36780",
                "1002",
                "第二章",
                Arrays.asList("完整正文一", "完整正文二"),
                "1001",
                "1003",
                1,
                12,
                true,
                Collections.<Integer>emptyList()
        );

        CountDownLatch saveLatch = new CountDownLatch(1);
        contentRepository.saveChapter(chapter, "REMOTE", new ContentTestCompletionCallback(saveLatch));
        assertTrue(saveLatch.await(5, TimeUnit.SECONDS));

        CountDownLatch ensureLatch = new CountDownLatch(1);
        ReaderTestDataCallback cb = new ReaderTestDataCallback(ensureLatch);
        readerRepository.ensureComplete("36780", "1002", RequestPriority.HIGH, "sub1", cb);
        assertTrue(ensureLatch.await(5, TimeUnit.SECONDS));

        assertNotNull(cb.getResult());
        assertTrue(cb.getResult().isComplete());
        assertEquals(0, connectionFactory.getRequestedUrls().size()); // No network
    }

    @Test
    public void testObserveDeliversCachedImmediatelyThenCompletes() throws Exception {
        // 1. Pre-populate partial/incomplete chapter
        ChapterResult partial = new ChapterResult(
                "36780",
                "1003",
                "第三章",
                Arrays.asList("部分正文"),
                "1002",
                null,
                1,
                4,
                false,
                Arrays.asList(1)
        );

        CountDownLatch saveLatch = new CountDownLatch(1);
        contentRepository.saveChapter(partial, "REMOTE", new ContentTestCompletionCallback(saveLatch));
        assertTrue(saveLatch.await(5, TimeUnit.SECONDS));

        // 2. Prepare mock HTML responses for network completion
        // page 0 has next link to page 1, page 1 has terminal next chapter 1004
        String p0Html = "<html><head><title>第三章</title></head><body><div id=\"content\"><p>第一段</p></div><a href=\"/book/36780/1003_1.html\">下一页</a></body></html>";
        String p1Html = "<html><head><title>第三章</title></head><body><div id=\"content\"><p>第二段</p></div><a href=\"/book/36780_1004.html\">下一章</a></body></html>";

        connectionFactory.addResponse("https://wanshuge.org/book/36780_1003.html", 200, p0Html.getBytes(StandardCharsets.UTF_8), null);
        connectionFactory.addResponse("https://wanshuge.org/book/36780/1003_1.html", 200, p1Html.getBytes(StandardCharsets.UTF_8), null);

        CountDownLatch cachedLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(1);
        ReaderTestObserver observer = new ReaderTestObserver(cachedLatch, completeLatch);

        readerRepository.observe("36780", "1003", "testSub", observer);

        // Cached result arrives first
        assertTrue(cachedLatch.await(3, TimeUnit.SECONDS));
        assertNotNull(observer.getCachedResult());
        assertEquals(1, observer.getCachedResult().getParagraphs().size());

        // Complete result arrives after background network fetch
        assertTrue(completeLatch.await(5, TimeUnit.SECONDS));
        assertNotNull(observer.getCompleteResult());
        assertTrue(observer.getCompleteResult().isComplete());
        assertEquals(2, observer.getCompleteResult().getParagraphs().size());
    }
}
