package org.wanshu.reader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wanshu.reader.core.text.TextBlockBuilder;
import org.wanshu.reader.data.content.ContentDatabase;
import org.wanshu.reader.data.content.entity.BookEntity;
import org.wanshu.reader.data.content.entity.ChapterBlockEntity;
import org.wanshu.reader.data.content.entity.ChapterEntity;
import org.wanshu.reader.data.content.entity.TocEntryEntity;
import org.wanshu.reader.data.repository.ContentRepository;
import org.wanshu.reader.data.txt.TxtExporter;
import org.wanshu.reader.data.txt.TxtImporter;

@RunWith(AndroidJUnit4.class)
public class TxtImportExportTest {
    private Context context;
    private File dbFile;
    private ContentDatabase db;
    private ContentRepository repository;
    private TextBlockBuilder textBlockBuilder;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        dbFile = new File(context.getFilesDir(), "test-txt-db.db");
        if (dbFile.exists()) {
            dbFile.delete();
        }
        db = Room.databaseBuilder(context, ContentDatabase.class, dbFile.getAbsolutePath())
                .allowMainThreadQueries()
                .build();
        textBlockBuilder = new TextBlockBuilder(2000);
        repository = new ContentRepository(db, Executors.newSingleThreadExecutor(), textBlockBuilder);
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
    public void testImportValidTxtCreatesReadyBookAndChapters() throws Exception {
        String txtContent = "第一章 归来\n这是归来的正文第一段。\n第二段正文。\n\n第二章 觉醒\n觉醒正文。\n\n第三章 突破\n突破正文。";
        ByteArrayInputStream in = new ByteArrayInputStream(txtContent.getBytes(StandardCharsets.UTF_8));

        CountDownLatch latch = new CountDownLatch(1);
        TestTxtImportListener listener = new TestTxtImportListener(latch);

        String bookId = TxtImporter.importStream(in, "测试TXT书籍", db, textBlockBuilder, listener);
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNotNull(bookId);
        assertEquals(3, listener.getChaptersCount());

        // 1. Check book record
        BookEntity book = db.bookDao().getBook(bookId);
        assertNotNull(book);
        assertEquals("测试TXT书籍", book.title);
        assertEquals("READY", book.importStatus);
        assertEquals("LOCAL_TXT", book.sourceType);
        assertEquals(3, book.chapterCount);

        // 2. Check chapters
        ChapterEntity ch1 = db.chapterDao().getChapter(bookId, "1");
        assertNotNull(ch1);
        assertEquals("第一章 归来", ch1.title);
        assertTrue(ch1.isComplete);
        assertNull(ch1.prevChapterId);
        assertEquals("2", ch1.nextChapterId);

        ChapterEntity ch2 = db.chapterDao().getChapter(bookId, "2");
        assertNotNull(ch2);
        assertEquals("第二章 觉醒", ch2.title);
        assertTrue(ch2.isComplete);
        assertEquals("1", ch2.prevChapterId);
        assertEquals("3", ch2.nextChapterId);

        ChapterEntity ch3 = db.chapterDao().getChapter(bookId, "3");
        assertNotNull(ch3);
        assertEquals("第三章 突破", ch3.title);
        assertTrue(ch3.isComplete);
        assertEquals("2", ch3.prevChapterId);
        assertNull(ch3.nextChapterId);

        // 3. Check blocks
        List<ChapterBlockEntity> blocks1 = db.chapterBlockDao().getBlocks(bookId, "1");
        assertNotNull(blocks1);
        assertTrue(blocks1.size() >= 1);
        assertTrue(blocks1.get(0).content.contains("这是归来的正文第一段。"));

        // 4. Check TOC entries
        List<TocEntryEntity> tocEntries = db.tocDao().getEntries(bookId);
        assertEquals(3, tocEntries.size());
        assertEquals("第一章 归来", tocEntries.get(0).title);
        assertEquals("第二章 觉醒", tocEntries.get(1).title);
        assertEquals("第三章 突破", tocEntries.get(2).title);

        // 5. Test export
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int exportedCount = TxtExporter.exportBook(bookId, db, out);
        assertEquals(3, exportedCount);

        String exportedText = new String(out.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(exportedText.contains("第一章 归来"));
        assertTrue(exportedText.contains("这是归来的正文第一段。"));
        assertTrue(exportedText.contains("第二章 觉醒"));
        assertTrue(exportedText.contains("第三章 突破"));

        // 6. Test delete local book
        CountDownLatch delLatch = new CountDownLatch(1);
        repository.deleteLocalBook(bookId, new ContentTestCompletionCallback(delLatch));
        assertTrue(delLatch.await(5, TimeUnit.SECONDS));

        assertNull(db.bookDao().getBook(bookId));
        assertEquals(0, db.chapterDao().getAllCachedChapterIds(bookId).size());
        assertEquals(0, db.tocDao().getEntriesCount(bookId));
    }

    @Test
    public void testImportEmptyStreamRollsBack() {
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[0]);
        CountDownLatch latch = new CountDownLatch(1);
        TestTxtImportListener listener = new TestTxtImportListener(latch);

        boolean failed = false;
        try {
            TxtImporter.importStream(in, "空书", db, textBlockBuilder, listener);
        } catch (Throwable t) {
            failed = true;
        }
        assertTrue(failed);

        // Verify no books exist
        List<BookEntity> books = db.bookDao().getAllBooks();
        assertEquals(0, books.size());
    }
}
