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
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wanshu.reader.core.model.ChapterResult;
import org.wanshu.reader.core.model.ReadingAnchor;
import org.wanshu.reader.core.net.FakeHttpConnectionFactory;
import org.wanshu.reader.core.net.SourceHttpClient;
import org.wanshu.reader.core.queue.RequestScheduler;
import org.wanshu.reader.core.text.TextBlockBuilder;
import org.wanshu.reader.core.util.JavaBase64Decoder;
import org.wanshu.reader.data.AppExecutors;
import org.wanshu.reader.data.content.ContentDatabase;
import org.wanshu.reader.data.content.entity.BookEntity;
import org.wanshu.reader.data.personal.PersonalDatabase;
import org.wanshu.reader.data.repository.ContentRepository;
import org.wanshu.reader.data.repository.PersonalRepository;
import org.wanshu.reader.data.repository.ReaderRepository;
import org.wanshu.reader.navigation.AppNavigator;
import org.wanshu.reader.navigation.BackController;
import org.wanshu.reader.navigation.BookRoute;
import org.wanshu.reader.ui.reader.ReaderController;
import org.wanshu.reader.ui.reader.ReaderView;

@RunWith(AndroidJUnit4.class)
public class BookSwitchingTest {
    private Context context;
    private File personalDbFile;
    private File contentDbFile;
    private PersonalDatabase personalDb;
    private ContentDatabase contentDb;
    private PersonalRepository personalRepo;
    private ContentRepository contentRepo;
    private RequestScheduler scheduler;
    private ReaderRepository readerRepo;
    private AppNavigator navigator;
    private BackController backController;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        personalDbFile = new File(context.getFilesDir(), "test-ab-personal.db");
        contentDbFile = new File(context.getFilesDir(), "test-ab-content.db");
        if (personalDbFile.exists()) personalDbFile.delete();
        if (contentDbFile.exists()) contentDbFile.delete();

        personalDb = Room.databaseBuilder(context, PersonalDatabase.class, personalDbFile.getAbsolutePath())
                .allowMainThreadQueries()
                .build();
        contentDb = Room.databaseBuilder(context, ContentDatabase.class, contentDbFile.getAbsolutePath())
                .allowMainThreadQueries()
                .build();

        personalRepo = new PersonalRepository(personalDb, Executors.newSingleThreadExecutor());
        contentRepo = new ContentRepository(contentDb, Executors.newSingleThreadExecutor(), new TextBlockBuilder(2000));

        scheduler = new RequestScheduler();
        SourceHttpClient httpClient = new SourceHttpClient(new FakeHttpConnectionFactory(), false);
        readerRepo = new ReaderRepository(contentRepo, scheduler, httpClient, new JavaBase64Decoder());

        navigator = new AppNavigator();
        backController = new BackController(navigator);
    }

    @After
    public void tearDown() {
        if (scheduler != null) scheduler.shutdown();
        if (personalDb != null) personalDb.close();
        if (contentDb != null) contentDb.close();
        if (personalDbFile != null && personalDbFile.exists()) personalDbFile.delete();
        if (contentDbFile != null && contentDbFile.exists()) contentDbFile.delete();
    }

    @Test
    public void testABBookSwitchingNoProgressCrossContamination() throws Exception {
        // 1. Seed Book A (Online "36780")
        BookEntity bookA = new BookEntity();
        bookA.bookId = "36780";
        bookA.title = "万古神帝";
        bookA.sourceType = "ONLINE";
        bookA.importStatus = "READY";
        contentDb.bookDao().insertBook(bookA);

        ChapterResult chapA = new ChapterResult(
                "36780",
                "1",
                "第一章 八百年后",
                Arrays.asList("张若尘醒来...", "八百年过去，沧海桑田。"),
                null,
                "2",
                1,
                24,
                true,
                Collections.<Integer>emptyList()
        );
        CountDownLatch saveLatchA = new CountDownLatch(1);
        contentRepo.saveChapter(chapA, "REMOTE", new ContentTestCompletionCallback(saveLatchA));
        assertTrue(saveLatchA.await(5, TimeUnit.SECONDS));

        // 2. Seed Book B (Local "local-novel")
        BookEntity bookB = new BookEntity();
        bookB.bookId = "local-novel";
        bookB.title = "本地神作";
        bookB.sourceType = "LOCAL_TXT";
        bookB.importStatus = "READY";
        contentDb.bookDao().insertBook(bookB);

        ChapterResult chapB = new ChapterResult(
                "local-novel",
                "1",
                "第一章 重生之日",
                Arrays.asList("林枫重活一世...", "万道争锋，谁与争锋。"),
                null,
                "2",
                1,
                24,
                true,
                Collections.<Integer>emptyList()
        );
        CountDownLatch saveLatchB = new CountDownLatch(1);
        contentRepo.saveChapter(chapB, "LOCAL", new ContentTestCompletionCallback(saveLatchB));
        assertTrue(saveLatchB.await(5, TimeUnit.SECONDS));

        // 3. User reads Book A chapter 1 -> reaches paragraph 1, offset 10 -> saves progress
        navigator.openReader("36780", "1");
        ReadingAnchor anchorA = new ReadingAnchor("36780", "1", 1, 10, System.currentTimeMillis());
        CountDownLatch progLatchA = new CountDownLatch(1);
        personalRepo.saveReadingProgress(anchorA, new ContentTestCompletionCallback(progLatchA));
        assertTrue(progLatchA.await(5, TimeUnit.SECONDS));

        // 4. User navigates back to shelf
        assertTrue(backController.handleBack());
        assertTrue(navigator.getCurrentRoute().isShelf());

        // 5. User reads Book B chapter 1 -> reaches paragraph 0, offset 5 -> saves progress
        navigator.openReader("local-novel", "1");
        ReadingAnchor anchorB = new ReadingAnchor("local-novel", "1", 0, 5, System.currentTimeMillis());
        CountDownLatch progLatchB = new CountDownLatch(1);
        personalRepo.saveReadingProgress(anchorB, new ContentTestCompletionCallback(progLatchB));
        assertTrue(progLatchB.await(5, TimeUnit.SECONDS));

        // 6. User navigates back to shelf
        assertTrue(backController.handleBack());
        assertTrue(navigator.getCurrentRoute().isShelf());

        // 7. Re-open Book A -> query progress
        CountDownLatch queryLatchA = new CountDownLatch(1);
        AnchorTestDataCallback cbA = new AnchorTestDataCallback(queryLatchA);
        personalRepo.getReadingProgress("36780", cbA);
        assertTrue(queryLatchA.await(5, TimeUnit.SECONDS));

        assertNotNull(cbA.getAnchor());
        assertEquals("36780", cbA.getAnchor().getBookId());
        assertEquals("1", cbA.getAnchor().getChapterId());
        assertEquals(1, cbA.getAnchor().getParagraphIndex());
        assertEquals(10, cbA.getAnchor().getOffsetUtf16());

        // 8. Query Book B progress -> verify it was NOT overwritten by Book A
        CountDownLatch queryLatchB = new CountDownLatch(1);
        AnchorTestDataCallback cbB = new AnchorTestDataCallback(queryLatchB);
        personalRepo.getReadingProgress("local-novel", cbB);
        assertTrue(queryLatchB.await(5, TimeUnit.SECONDS));

        assertNotNull(cbB.getAnchor());
        assertEquals("local-novel", cbB.getAnchor().getBookId());
        assertEquals("1", cbB.getAnchor().getChapterId());
        assertEquals(0, cbB.getAnchor().getParagraphIndex());
        assertEquals(5, cbB.getAnchor().getOffsetUtf16());

        // 9. Verify chapter contents remain isolated
        CountDownLatch chLatchA = new CountDownLatch(1);
        ReaderTestDataCallback chCbA = new ReaderTestDataCallback(chLatchA);
        contentRepo.getChapter("36780", "1", chCbA);
        assertTrue(chLatchA.await(5, TimeUnit.SECONDS));
        assertEquals("第一章 八百年后", chCbA.getResult().getTitle());
        assertTrue(chCbA.getResult().getParagraphs().get(0).contains("张若尘醒来"));

        CountDownLatch chLatchB = new CountDownLatch(1);
        ReaderTestDataCallback chCbB = new ReaderTestDataCallback(chLatchB);
        contentRepo.getChapter("local-novel", "1", chCbB);
        assertTrue(chLatchB.await(5, TimeUnit.SECONDS));
        assertEquals("第一章 重生之日", chCbB.getResult().getTitle());
        assertTrue(chCbB.getResult().getParagraphs().get(0).contains("林枫重活一世"));
    }

    @Test
    public void testGenerationProtectionDiscardsLateResponses() {
        ReaderView readerView = new ReaderView(context);
        ReaderController controller = new ReaderController(
                readerView,
                readerRepo,
                personalRepo,
                navigator,
                AppExecutors.getInstance(),
                new TextBlockBuilder(2000)
        );

        // 1. Open Book A -> generation G1
        BookRoute routeA = navigator.openReader("36780", "1");
        long g1 = routeA.getGeneration();
        controller.open(routeA);

        // 2. User quickly returns to shelf -> generation G2
        navigator.openShelf();
        long g2 = navigator.getCurrentGeneration();
        assertTrue(g2 > g1);

        // 3. User opens Book B -> generation G3
        BookRoute routeB = navigator.openReader("local-novel", "1");
        long g3 = routeB.getGeneration();
        controller.open(routeB);
        assertTrue(g3 > g2);

        // 4. A late response for Book A with generation G1 arrives
        ChapterResult lateBookAResult = new ChapterResult(
                "36780",
                "1",
                "万古神帝第一章",
                Arrays.asList("张若尘正文"),
                null,
                "2",
                1,
                10,
                true,
                Collections.<Integer>emptyList()
        );
        controller.onChapterLoaded(lateBookAResult, g1, true);

        // Generation protection: the active route must remain Book B at g3
        assertEquals("local-novel", navigator.getCurrentRoute().getBookId());
        assertEquals(g3, navigator.getCurrentGeneration());
        assertFalse(navigator.isCurrentGeneration(g1));
    }
}
