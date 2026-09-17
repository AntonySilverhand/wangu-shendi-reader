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
import java.util.List;
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
import org.wanshu.reader.data.content.entity.TocPageEntity;
import org.wanshu.reader.data.personal.PersonalDatabase;
import org.wanshu.reader.data.personal.entity.BookmarkEntity;
import org.wanshu.reader.data.personal.entity.MigrationRunEntity;
import org.wanshu.reader.data.personal.entity.ReadingHistoryEntity;
import org.wanshu.reader.data.personal.entity.ReadingProgressEntity;
import org.wanshu.reader.data.personal.entity.SettingsEntity;
import org.wanshu.reader.migration.LegacyMigrationEngine;

@RunWith(AndroidJUnit4.class)
public class LegacyMigrationIntegrationTest {
    private Context context;
    private File personalDbFile;
    private File contentDbFile;
    private PersonalDatabase personalDb;
    private ContentDatabase contentDb;
    private LegacyMigrationEngine engine;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        personalDbFile = new File(context.getFilesDir(), "test-mig-personal.db");
        contentDbFile = new File(context.getFilesDir(), "test-mig-content.db");

        if (personalDbFile.exists()) personalDbFile.delete();
        if (contentDbFile.exists()) contentDbFile.delete();

        personalDb = Room.databaseBuilder(context, PersonalDatabase.class, personalDbFile.getAbsolutePath())
                .allowMainThreadQueries()
                .build();
        contentDb = Room.databaseBuilder(context, ContentDatabase.class, contentDbFile.getAbsolutePath())
                .allowMainThreadQueries()
                .build();

        engine = new LegacyMigrationEngine(personalDb, contentDb, new TextBlockBuilder());
    }

    @After
    public void tearDown() {
        if (personalDb != null) personalDb.close();
        if (contentDb != null) contentDb.close();
        if (personalDbFile != null && personalDbFile.exists()) personalDbFile.delete();
        if (contentDbFile != null && contentDbFile.exists()) contentDbFile.delete();
    }

    @Test
    public void testLocalStorageImportAndProgressSafety() throws Exception {
        // Pre-condition: native app already has newer reading progress for 36780
        ReadingProgressEntity existingNativeProg = new ReadingProgressEntity();
        existingNativeProg.bookId = "36780";
        existingNativeProg.chapterId = "200";
        existingNativeProg.paragraphIndex = 8;
        existingNativeProg.offsetUtf16 = 15;
        existingNativeProg.updatedAt = 5000L;
        existingNativeProg.sequenceNumber = 1L;
        personalDb.readingProgressDao().saveProgress(existingNativeProg);

        String settingsJson = "{\n"
                + "  \"theme\": \"black\",\n"
                + "  \"font\": \"hei\",\n"
                + "  \"fontSize\": 22,\n"
                + "  \"lineHeight\": 1.9,\n"
                + "  \"margin\": 24,\n"
                + "  \"prefetch\": true\n"
                + "}";

        String personalJson = "{\n"
                + "  \"version\": 1,\n"
                + "  \"books\": {\n"
                + "    \"36780\": {\n"
                + "      \"progress\": {\n"
                + "        \"chapterId\": \"150\",\n"
                + "        \"paragraph\": 2,\n"
                + "        \"offset\": 0,\n"
                + "        \"updatedAt\": 3000\n"
                + "      },\n"
                + "      \"bookmarks\": [{\n"
                + "        \"id\": \"bm-old-1\",\n"
                + "        \"chapterId\": \"150\",\n"
                + "        \"paragraph\": 2,\n"
                + "        \"offset\": 0,\n"
                + "        \"excerpt\": \"旧书签内容\",\n"
                + "        \"createdAt\": 3000\n"
                + "      }],\n"
                + "      \"history\": [{\n"
                + "        \"chapterId\": \"150\",\n"
                + "        \"chapterTitle\": \"第150章\",\n"
                + "        \"at\": 3000\n"
                + "      }]\n"
                + "    },\n"
                + "    \"local-101\": {\n"
                + "      \"progress\": {\n"
                + "        \"chapterId\": \"3\",\n"
                + "        \"paragraph\": 1,\n"
                + "        \"offset\": 5,\n"
                + "        \"updatedAt\": 4000\n"
                + "      },\n"
                + "      \"bookmarks\": [],\n"
                + "      \"history\": []\n"
                + "    }\n"
                + "  }\n"
                + "}";

        String localBooksJson = "[\n"
                + "  {\n"
                + "    \"id\": \"local-101\",\n"
                + "    \"title\": \"本地测试书\",\n"
                + "    \"chapters\": 10,\n"
                + "    \"bytes\": 45000,\n"
                + "    \"importedAt\": 3500\n"
                + "  }\n"
                + "]";

        engine.importLocalStorage(settingsJson, personalJson, localBooksJson);

        // 1. Verify Settings mapped correctly
        SettingsEntity s = personalDb.settingsDao().getSettings();
        assertNotNull(s);
        assertEquals("oled", s.theme); // "black" mapped to "oled"
        assertEquals("sans", s.fontFamily); // "hei" mapped to "sans"
        assertEquals(22.0f, s.fontSize, 0.01f);

        // 2. Verify newer native progress was PRESERVED
        ReadingProgressEntity p36780 = personalDb.readingProgressDao().getProgress("36780");
        assertNotNull(p36780);
        assertEquals("200", p36780.chapterId);
        assertEquals(5000L, p36780.updatedAt);

        // 3. Verify local book progress was imported
        ReadingProgressEntity pLocal = personalDb.readingProgressDao().getProgress("local-101");
        assertNotNull(pLocal);
        assertEquals("3", pLocal.chapterId);
        assertEquals(4000L, pLocal.updatedAt);

        // 4. Verify bookmarks and history were imported
        List<BookmarkEntity> bms = personalDb.bookmarkDao().getBookmarks("36780");
        assertEquals(1, bms.size());
        assertEquals("bm-old-1", bms.get(0).id);
        assertEquals("旧书签内容", bms.get(0).snippet);

        List<ReadingHistoryEntity> history = personalDb.readingHistoryDao().getHistoryForBook("36780");
        assertEquals(1, history.size());
        assertEquals("第150章", history.get(0).title);

        // 5. Verify local book meta was imported into contentDb
        BookEntity localBook = contentDb.bookDao().getBook("local-101");
        assertNotNull(localBook);
        assertEquals("本地测试书", localBook.title);
        assertEquals("LOCAL_TXT", localBook.sourceType);
        assertEquals(10, localBook.chapterCount);
    }

    @Test
    public void testChaptersBatchImportAndProtection() throws Exception {
        // Pre-insert complete chapter 10
        ChapterEntity preChap = new ChapterEntity();
        preChap.bookId = "36780";
        preChap.chapterId = "10";
        preChap.title = "第10章 已完整";
        preChap.isComplete = true;
        preChap.charCount = 5000;
        preChap.source = "REMOTE";
        contentDb.chapterDao().insertChapter(preChap);

        String batchJson = "[\n"
                + "  {\n"
                + "    \"bookId\": \"36780\",\n"
                + "    \"chapterId\": \"10\",\n"
                + "    \"title\": \"第10章 旧不完整版本\",\n"
                + "    \"paragraphs\": [\"段落A\"],\n"
                + "    \"complete\": false,\n"
                + "    \"source\": \"remote\"\n"
                + "  },\n"
                + "  {\n"
                + "    \"bookId\": \"36780\",\n"
                + "    \"chapterId\": \"11\",\n"
                + "    \"title\": \"第11章 新迁移章\",\n"
                + "    \"paragraphs\": [\"第一段内容\", \"第二段正文内容\", \"第三段结尾\"],\n"
                + "    \"complete\": true,\n"
                + "    \"source\": \"remote\",\n"
                + "    \"prevId\": \"10\",\n"
                + "    \"nextId\": \"12\",\n"
                + "    \"fetchedAt\": 1690000000000\n"
                + "  },\n"
                + "  {\n"
                + "    \"bookId\": \"local-101\",\n"
                + "    \"chapterId\": \"1\",\n"
                + "    \"title\": \"本地书第1章\",\n"
                + "    \"paragraphs\": [\"本地文本第一段\", \"本地文本第二段\"],\n"
                + "    \"complete\": true,\n"
                + "    \"source\": \"local\",\n"
                + "    \"prevId\": null,\n"
                + "    \"nextId\": \"2\"\n"
                + "  }\n"
                + "]";

        int importedCount = engine.importChaptersBatch(batchJson);
        assertEquals(3, importedCount);

        // Verify chapter 10 remains complete and was not downgraded
        ChapterEntity c10 = contentDb.chapterDao().getChapter("36780", "10");
        assertNotNull(c10);
        assertTrue(c10.isComplete);
        assertEquals("第10章 已完整", c10.title);

        // Verify chapter 11 was imported and blocks created
        ChapterEntity c11 = contentDb.chapterDao().getChapter("36780", "11");
        assertNotNull(c11);
        assertTrue(c11.isComplete);
        assertEquals("第11章 新迁移章", c11.title);
        assertEquals("10", c11.prevChapterId);
        assertEquals("12", c11.nextChapterId);
        List<ChapterBlockEntity> blocks11 = contentDb.chapterBlockDao().getBlocks("36780", "11");
        assertFalse(blocks11.isEmpty());
        assertTrue(blocks11.get(0).content.contains("第一段内容"));

        // Verify local chapter was imported with source = LOCAL
        ChapterEntity cLocal = contentDb.chapterDao().getChapter("local-101", "1");
        assertNotNull(cLocal);
        assertEquals("LOCAL", cLocal.source);
        assertEquals("本地书第1章", cLocal.title);
    }

    @Test
    public void testTocBatchImport() throws Exception {
        String tocJson = "[\n"
                + "  {\n"
                + "    \"bookId\": \"36780\",\n"
                + "    \"totalPages\": 2,\n"
                + "    \"updatedAt\": 1690000000000,\n"
                + "    \"pages\": {\n"
                + "      \"1\": [\n"
                + "        {\"chapterId\": \"1\", \"title\": \"第一章\", \"orderIndex\": 0, \"isExtra\": false},\n"
                + "        {\"chapterId\": \"2\", \"title\": \"第二章\", \"orderIndex\": 1, \"isExtra\": false}\n"
                + "      ],\n"
                + "      \"2\": [\n"
                + "        {\"chapterId\": \"3\", \"title\": \"第三章\", \"orderIndex\": 0, \"isExtra\": false},\n"
                + "        {\"chapterId\": \"extra-1\", \"title\": \"番外一\", \"orderIndex\": 1, \"isExtra\": true}\n"
                + "      ]\n"
                + "    }\n"
                + "  }\n"
                + "]";

        int totalEntries = engine.importTocBatch(tocJson);
        assertEquals(4, totalEntries);

        List<TocPageEntity> pages = contentDb.tocDao().getAllTocPages("36780");
        assertEquals(2, pages.size());

        List<TocEntryEntity> entries = contentDb.tocDao().getEntries("36780");
        assertEquals(4, entries.size());
        assertEquals("第一章", entries.get(0).title);
        assertFalse(entries.get(0).isExtra);
        assertEquals("番外一", entries.get(3).title);
        assertTrue(entries.get(3).isExtra);
    }

    @Test
    public void testMigrationIdempotency() throws Exception {
        String batchJson = "[\n"
                + "  {\n"
                + "    \"bookId\": \"36780\",\n"
                + "    \"chapterId\": \"20\",\n"
                + "    \"title\": \"第20章\",\n"
                + "    \"paragraphs\": [\"内容段落\"],\n"
                + "    \"complete\": true,\n"
                + "    \"source\": \"remote\"\n"
                + "  }\n"
                + "]";

        // Run once
        engine.importChaptersBatch(batchJson);
        ChapterEntity first = contentDb.chapterDao().getChapter("36780", "20");
        assertNotNull(first);

        // Run twice
        engine.importChaptersBatch(batchJson);
        ChapterEntity second = contentDb.chapterDao().getChapter("36780", "20");
        assertNotNull(second);
        assertEquals(first.charCount, second.charCount);

        List<ChapterBlockEntity> blocks = contentDb.chapterBlockDao().getBlocks("36780", "20");
        assertEquals(1, blocks.size());
    }

    @Test
    public void testCompleteAndRecordFailure() {
        engine.completeMigration("run-1", "{\"chaptersCount\": 50}");
        MigrationRunEntity run = personalDb.migrationRunDao().getLatestRun();
        assertNotNull(run);
        assertEquals("COMPLETE", run.phase);
        assertEquals("completed", run.checkpoint);
        assertTrue(run.summary.contains("50"));

        engine.recordFailure("run-2", "chapters_read", "Simulated disk timeout");
        MigrationRunEntity failedRun = personalDb.migrationRunDao().getLatestRun();
        assertNotNull(failedRun);
        assertEquals("FAILED", failedRun.phase);
        assertEquals("chapters_read", failedRun.checkpoint);
        assertEquals("Simulated disk timeout", failedRun.error);
    }
}
