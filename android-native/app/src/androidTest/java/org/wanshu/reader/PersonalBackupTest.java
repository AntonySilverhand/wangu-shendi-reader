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
import org.wanshu.reader.data.personal.PersonalBackupHelper;
import org.wanshu.reader.data.personal.PersonalDatabase;
import org.wanshu.reader.data.personal.entity.BookmarkEntity;
import org.wanshu.reader.data.personal.entity.ReadingHistoryEntity;
import org.wanshu.reader.data.personal.entity.ReadingProgressEntity;
import org.wanshu.reader.data.personal.entity.SettingsEntity;

@RunWith(AndroidJUnit4.class)
public class PersonalBackupTest {
    private Context context;
    private File dbFile;
    private PersonalDatabase db;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        dbFile = new File(context.getFilesDir(), "test-backup-personal.db");
        if (dbFile.exists()) {
            dbFile.delete();
        }
        db = Room.databaseBuilder(context, PersonalDatabase.class, dbFile.getAbsolutePath())
                .allowMainThreadQueries()
                .build();
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
    public void testExportToJson() throws Exception {
        SettingsEntity settings = new SettingsEntity();
        settings.theme = "dark";
        settings.fontSize = 20.0f;
        db.settingsDao().saveSettings(settings);

        ReadingProgressEntity p = new ReadingProgressEntity();
        p.bookId = "36780";
        p.chapterId = "100";
        p.paragraphIndex = 5;
        p.offsetUtf16 = 20;
        p.updatedAt = 123456789L;
        p.sequenceNumber = 1L;
        db.readingProgressDao().saveProgress(p);

        BookmarkEntity bm = new BookmarkEntity();
        bm.id = "bm-1";
        bm.bookId = "36780";
        bm.chapterId = "100";
        bm.paragraphIndex = 5;
        bm.offsetUtf16 = 20;
        bm.snippet = "测试书签摘要";
        bm.createdAt = 123456789L;
        db.bookmarkDao().insertBookmark(bm);

        List<ReadingProgressEntity> progressList = db.readingProgressDao().getAllProgress();
        List<BookmarkEntity> bookmarks = db.bookmarkDao().getAllBookmarks();
        List<ReadingHistoryEntity> history = db.readingHistoryDao().getAllHistory();

        String json = PersonalBackupHelper.exportToJson(settings, progressList, bookmarks, history);
        assertNotNull(json);
        assertTrue(json.contains("\"app\": \"wangu-shendi-reader\""));
        assertTrue(json.contains("\"version\": 1"));
        assertTrue(json.contains("\"theme\": \"dark\""));
        assertTrue(json.contains("\"chapterId\": \"100\""));
        assertTrue(json.contains("测试书签摘要"));
    }

    @Test
    public void testImportFromJsonMerge() throws Exception {
        BookmarkEntity bm1 = new BookmarkEntity();
        bm1.id = "bm-local-1";
        bm1.bookId = "local-book-1";
        bm1.chapterId = "1";
        bm1.paragraphIndex = 2;
        bm1.offsetUtf16 = 0;
        bm1.snippet = "旧书签";
        bm1.createdAt = 1000L;
        db.bookmarkDao().insertBookmark(bm1);

        String importJson = "{\n"
                + "  \"app\": \"wangu-shendi-reader\",\n"
                + "  \"version\": 1,\n"
                + "  \"settings\": {\"theme\": \"oled\", \"fontSize\": 22.0},\n"
                + "  \"personal\": {\n"
                + "    \"books\": {\n"
                + "      \"36780\": {\n"
                + "        \"bookmarks\": [{\n"
                + "          \"id\": \"bm-imported-1\",\n"
                + "          \"chapterId\": \"50\",\n"
                + "          \"paragraphIndex\": 10,\n"
                + "          \"offsetUtf16\": 5,\n"
                + "          \"snippet\": \"新导入书签\",\n"
                + "          \"createdAt\": 2000\n"
                + "        }],\n"
                + "        \"progress\": {\n"
                + "          \"chapterId\": \"50\",\n"
                + "          \"paragraphIndex\": 10,\n"
                + "          \"offsetUtf16\": 5,\n"
                + "          \"updatedAt\": 2000\n"
                + "        }\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "}";

        boolean success = PersonalBackupHelper.importFromJson(importJson, db, false);
        assertTrue(success);

        SettingsEntity s = db.settingsDao().getSettings();
        assertNotNull(s);
        assertEquals("oled", s.theme);
        assertEquals(22.0f, s.fontSize, 0.01f);

        // Both bookmarks must exist (merge)
        List<BookmarkEntity> allBm = db.bookmarkDao().getAllBookmarks();
        assertEquals(2, allBm.size());

        ReadingProgressEntity p = db.readingProgressDao().getProgress("36780");
        assertNotNull(p);
        assertEquals("50", p.chapterId);
    }

    @Test
    public void testImportMalformedJson() {
        boolean success = PersonalBackupHelper.importFromJson("not json content", db, false);
        assertFalse(success);
    }
}
