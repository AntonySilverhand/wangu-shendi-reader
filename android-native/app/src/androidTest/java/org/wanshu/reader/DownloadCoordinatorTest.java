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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wanshu.reader.core.download.DownloadDemandFlags;
import org.wanshu.reader.core.download.DownloadPriority;
import org.wanshu.reader.core.download.DownloadRange;
import org.wanshu.reader.core.download.DownloadTaskState;
import org.wanshu.reader.core.net.FakeHttpConnectionFactory;
import org.wanshu.reader.core.net.SourceHttpClient;
import org.wanshu.reader.core.queue.RequestScheduler;
import org.wanshu.reader.core.text.TextBlockBuilder;
import org.wanshu.reader.core.util.JavaBase64Decoder;
import org.wanshu.reader.data.AppExecutors;
import org.wanshu.reader.data.content.ContentDatabase;
import org.wanshu.reader.data.content.entity.BookEntity;
import org.wanshu.reader.data.content.entity.ChapterEntity;
import org.wanshu.reader.data.content.entity.DownloadPlanEntity;
import org.wanshu.reader.data.content.entity.DownloadPolicyEntity;
import org.wanshu.reader.data.content.entity.DownloadTaskEntity;
import org.wanshu.reader.data.content.entity.TocEntryEntity;
import org.wanshu.reader.data.content.entity.TocPageEntity;
import org.wanshu.reader.download.DownloadCoordinator;
import org.wanshu.reader.download.GateResult;
import org.wanshu.reader.download.PauseReason;
import org.wanshu.reader.download.ReadingSessionGate;

@RunWith(AndroidJUnit4.class)
public class DownloadCoordinatorTest {

    private Context context;
    private File dbFile;
    private ContentDatabase db;
    private RequestScheduler scheduler;
    private FakeHttpConnectionFactory connectionFactory;
    private SourceHttpClient httpClient;
    private AppExecutors executors;
    private DownloadCoordinator coordinator;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        dbFile = new File(context.getFilesDir(), "test-download-coordinator.db");
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

        // Pre-populate TOC page 1 marked LOADED so it doesn't need to load all 44 pages over network
        TocPageEntity page = new TocPageEntity();
        page.bookId = "36780";
        page.pageIndex = 1;
        page.status = "LOADED";
        page.updatedAt = System.currentTimeMillis();
        db.tocDao().insertTocPage(page);

        // Pre-populate TOC entries for chapter 1 and chapter 2
        List<TocEntryEntity> entries = new ArrayList<TocEntryEntity>();
        TocEntryEntity e1 = new TocEntryEntity();
        e1.bookId = "36780";
        e1.chapterId = "1";
        e1.title = "第一章 八百年后";
        e1.orderKey = 1;
        e1.isExtra = false;
        e1.sourcePageIndex = 1;
        entries.add(e1);

        TocEntryEntity e2 = new TocEntryEntity();
        e2.bookId = "36780";
        e2.chapterId = "2";
        e2.title = "第二章 开启神武印记";
        e2.orderKey = 2;
        e2.isExtra = false;
        e2.sourcePageIndex = 1;
        entries.add(e2);
        db.tocDao().insertEntries(entries);

        scheduler = new RequestScheduler();
        connectionFactory = new FakeHttpConnectionFactory();
        httpClient = new SourceHttpClient(connectionFactory, false);
        executors = AppExecutors.getInstance();

        coordinator = new DownloadCoordinator(
                db,
                scheduler,
                httpClient,
                executors,
                new TextBlockBuilder(4000),
                new JavaBase64Decoder()
        );
    }

    @After
    public void tearDown() {
        if (coordinator != null) {
            coordinator.setAppForeground(false);
        }
        if (db != null) {
            db.close();
        }
        if (dbFile != null && dbFile.exists()) {
            dbFile.delete();
        }
    }

    @Test
    public void testZeroNetworkSkipOnAlreadyCached() throws Exception {
        // Pre-cache chapter 1 as complete in DB
        ChapterEntity ch1 = new ChapterEntity();
        ch1.bookId = "36780";
        ch1.chapterId = "1";
        ch1.title = "第一章 八百年后";
        ch1.isComplete = true;
        ch1.charCount = 3000;
        ch1.bytes = 9000;
        ch1.source = "REMOTE";
        ch1.fetchedAt = System.currentTimeMillis();
        db.chapterDao().insertChapter(ch1);

        CountDownLatch latch = new CountDownLatch(1);
        TestDownloadProgressListener listener = new TestDownloadProgressListener(latch);
        coordinator.addListener(listener);

        coordinator.startManualDownload("36780", "1", DownloadRange.NEXT_50);

        latch.await(3, TimeUnit.SECONDS);

        // Chapter 1 must NOT have any network request
        for (String url : connectionFactory.getRequestedUrls()) {
            assertFalse(url.contains("_1.html") || url.contains("36780_1"));
        }

        coordinator.removeListener(listener);
    }

    @Test
    public void testForegroundGatePausesAndResumes() {
        assertTrue(coordinator.isAppForeground());
        assertTrue(coordinator.isReadyToRun() == false); // no active book yet

        coordinator.startManualDownload("36780", "1", DownloadRange.NEXT_50);
        assertTrue(coordinator.isReadyToRun());

        // App leaves foreground
        coordinator.setAppForeground(false);
        assertFalse(coordinator.isAppForeground());
        assertFalse(coordinator.isReadyToRun());

        // App returns to foreground
        coordinator.setAppForeground(true);
        assertTrue(coordinator.isAppForeground());
        assertTrue(coordinator.isReadyToRun());
    }

    @Test
    public void testPauseResumeAndCancel() throws Exception {
        coordinator.startManualDownload("36780", "1", DownloadRange.NEXT_50);
        assertTrue(coordinator.isReadyToRun());

        coordinator.pauseDownload("36780");
        assertFalse(coordinator.isReadyToRun());

        coordinator.resumeDownload("36780");
        assertTrue(coordinator.isReadyToRun());

        coordinator.cancelDownload("36780");
        assertFalse(coordinator.isReadyToRun());

        // Wait a bit for cancel task to clear DB
        Thread.sleep(500);
        int pending = db.downloadTaskDao().getPendingCount("36780");
        assertEquals(0, pending);
    }

    @Test
    public void testAutoCachePlanAndElevation() throws Exception {
        // Setup policy: autoCacheEnabled = true
        DownloadPolicyEntity policy = new DownloadPolicyEntity();
        policy.bookId = "36780";
        policy.autoCacheEnabled = true;
        policy.scope = "ENTIRE_BOOK";
        policy.allowMetered = true;
        policy.maxCacheBytes = 256L * 1024L * 1024L;
        db.downloadPolicyDao().savePolicy(policy);

        // Notify reading chapter 1
        coordinator.onReadingChapterChanged("36780", "1", "第一章 八百年后");

        // Wait for background planner
        Thread.sleep(600);

        DownloadPlanEntity plan = db.downloadPlanDao().getPlan("36780");
        assertNotNull(plan);
        assertEquals("1", plan.initialAnchorChapterId);
        assertEquals("ENTIRE_BOOK", plan.scope);

        // Chapter 2 task should be created and elevated
        DownloadTaskEntity task2 = db.downloadTaskDao().getTask("36780", "2");
        assertNotNull(task2);
        assertTrue((task2.demandFlags & DownloadDemandFlags.DEMAND_PREFETCH) != 0);
        assertEquals(DownloadPriority.PREFETCH, task2.priority);
    }

    @Test
    public void testReadingSessionGate() {
        ReadingSessionGate gate = new ReadingSessionGate(context);

        DownloadPolicyEntity policy = new DownloadPolicyEntity();
        policy.bookId = "36780";
        policy.autoCacheEnabled = true;
        policy.allowMetered = true;
        policy.maxCacheBytes = 256L * 1024L * 1024L;

        // 1. Not resumed -> APP_HIDDEN
        gate.setActivityResumed(false);
        gate.setReaderActive(true, "36780");
        GateResult r1 = gate.evaluate("36780", policy, 0L);
        assertFalse(r1.allowed);
        assertEquals(PauseReason.APP_HIDDEN, r1.reason);

        // 2. Resumed, not reading -> NOT_READING
        gate.setActivityResumed(true);
        gate.setReaderActive(false, "");
        GateResult r2 = gate.evaluate("36780", policy, 0L);
        assertFalse(r2.allowed);
        assertEquals(PauseReason.NOT_READING, r2.reason);

        // 3. Local book -> NOT_READING (local books never trigger online auto-cache)
        gate.setReaderActive(true, "local-123");
        GateResult r3 = gate.evaluate("local-123", policy, 0L);
        assertFalse(r3.allowed);
        assertEquals(PauseReason.NOT_READING, r3.reason);

        // 4. Storage limit exceeded -> STORAGE_LOW
        gate.setReaderActive(true, "36780");
        long overLimit = policy.maxCacheBytes + 1024L;
        GateResult r4 = gate.evaluate("36780", policy, overLimit);
        assertFalse(r4.allowed);
        assertEquals(PauseReason.STORAGE_LOW, r4.reason);
    }
}
