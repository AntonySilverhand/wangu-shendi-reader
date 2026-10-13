package org.wanshu.reader;

import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wanshu.reader.core.model.ReadingAnchor;
import org.wanshu.reader.data.export.PersonalDataExporter;
import org.wanshu.reader.data.export.PersonalDataImporter;
import org.wanshu.reader.data.personal.PersonalDatabase;
import org.wanshu.reader.data.personal.entity.BookmarkEntity;
import org.wanshu.reader.data.personal.entity.ReadingHistoryEntity;
import org.wanshu.reader.data.personal.entity.ReadingProgressEntity;
import org.wanshu.reader.data.personal.entity.SettingsEntity;
import org.wanshu.reader.data.repository.SaveProgressRunnable;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PersonalBackupTest {
    private PersonalDatabase db;
    @Before public void createDb() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().getTargetContext(), PersonalDatabase.class).build();
        SettingsEntity settings = new SettingsEntity();
        settings.lineHeight = 1.3f; settings.autoCacheEnabled = true; settings.autoCacheMetered = true;
        db.settingsDao().saveSettings(settings);
        ReadingProgressEntity progress = new ReadingProgressEntity();
        progress.bookId = "36780"; progress.chapterId = "123"; progress.paragraphIndex = 2;
        progress.offsetUtf16 = 17; progress.updatedAt = 20; progress.sequenceNumber = 42;
        progress.quote = "正文😀"; progress.paragraphHash = "paragraph-hash"; progress.contentRevision = 5;
        db.readingProgressDao().saveProgress(progress);
        BookmarkEntity bookmark = new BookmarkEntity();
        bookmark.id = "mark-1"; bookmark.bookId = "36780"; bookmark.chapterId = "123";
        bookmark.paragraphIndex = 2; bookmark.offsetUtf16 = 17; bookmark.snippet = "正文😀"; bookmark.createdAt = 10;
        db.bookmarkDao().insertBookmark(bookmark);
        ReadingHistoryEntity history = new ReadingHistoryEntity();
        history.bookId = "36780"; history.chapterId = "123"; history.title = "标题"; history.readAt = 20;
        db.readingHistoryDao().insertHistory(history);
    }
    @After public void closeDb() { db.close(); }

    @Test public void roundTripIsRepeatableAndDoesNotStartDownloads() throws Exception {
        String json = PersonalDataExporter.exportToJson(db);
        PersonalDataImporter.importFromJson(db, json);
        PersonalDataImporter.importFromJson(db, json);
        assertEquals(1, db.bookmarkDao().getAllBookmarks().size());
        assertEquals(1, db.readingHistoryDao().getAllHistory().size());
        ReadingProgressEntity progress = db.readingProgressDao().getProgress("36780");
        assertEquals(17, progress.offsetUtf16); assertEquals("正文😀", progress.quote);
        assertEquals(5, progress.contentRevision); assertEquals(42, progress.sequenceNumber);
        SettingsEntity settings = db.settingsDao().getSettings();
        assertEquals(1.3f, settings.lineHeight, 0.0001f);
        assertFalse(settings.autoCacheEnabled); assertFalse(settings.autoCacheMetered);
    }

    @Test public void backupRestoresIntoEmptyPersonalDatabase() throws Exception {
        PersonalDatabase restored = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().getTargetContext(), PersonalDatabase.class).build();
        try {
            PersonalDataImporter.importFromJson(restored, PersonalDataExporter.exportToJson(db));
            ReadingProgressEntity progress = restored.readingProgressDao().getProgress("36780");
            assertNotNull(progress); assertEquals(17, progress.offsetUtf16);
            assertEquals("正文😀", progress.quote); assertEquals("paragraph-hash", progress.paragraphHash);
            assertEquals(1, restored.bookmarkDao().getAllBookmarks().size());
            assertEquals(1, restored.readingHistoryDao().getAllHistory().size());
            assertFalse(restored.settingsDao().getSettings().autoCacheEnabled);
        } finally { restored.close(); }
    }

    @Test public void newerImportDoesNotBlockSubsequentReaderSaves() throws Exception {
        JSONObject root = backup();
        JSONObject progress = book(root).getJSONObject("progress");
        progress.put("updatedAt", 30).put("offsetUtf16", 25);
        PersonalDataImporter.importFromJson(db, root.toString());
        assertEquals(25, db.readingProgressDao().getProgress("36780").offsetUtf16);
        new SaveProgressRunnable(db, new ReadingAnchor("36780", "123", 3, 8, 31), 43, null).run();
        assertEquals(8, db.readingProgressDao().getProgress("36780").offsetUtf16);
        assertEquals(43, db.readingProgressDao().getProgress("36780").sequenceNumber);
    }

    @Test public void malformedLateRecordRollsBackSettingsProgressAndBookmarks() throws Exception {
        JSONObject root = backup();
        root.getJSONObject("settings").put("fontSize", 24);
        book(root).getJSONObject("progress").put("updatedAt", 30).put("offsetUtf16", 50);
        JSONArray bookmarks = book(root).getJSONArray("bookmarks");
        JSONObject extra = new JSONObject(bookmarks.getJSONObject(0).toString());
        extra.put("id", "mark-2").put("offsetUtf16", 80); bookmarks.put(extra);
        JSONObject invalid = new JSONObject(extra.toString());
        invalid.put("id", "bad").put("paragraphIndex", -1); bookmarks.put(invalid);
        expectFailure(root.toString());
        assertEquals(18f, db.settingsDao().getSettings().fontSize, 0.001f);
        assertTrue(db.settingsDao().getSettings().autoCacheEnabled);
        assertEquals(17, db.readingProgressDao().getProgress("36780").offsetUtf16);
        assertEquals(1, db.bookmarkDao().getAllBookmarks().size());
    }

    @Test public void collidingBookmarkIdCannotOverwriteAnotherBook() throws Exception {
        JSONObject root = backup();
        BookmarkEntity other = db.bookmarkDao().getBookmark("mark-1");
        other.bookId = "local-other"; db.bookmarkDao().insertBookmark(other);
        PersonalDataImporter.importFromJson(db, root.toString());
        PersonalDataImporter.importFromJson(db, root.toString());
        assertEquals("local-other", db.bookmarkDao().getBookmark("mark-1").bookId);
        assertEquals(2, db.bookmarkDao().getAllBookmarks().size());
        assertEquals(1, db.bookmarkDao().getBookmarks("36780").size());
    }

    @Test public void historyIsMergedByChapterAndBoundedToThirty() throws Exception {
        JSONObject root = backup(); JSONArray history = new JSONArray();
        for (int i = 0; i < 40; i++) history.put(new JSONObject().put("chapterId", String.valueOf(i + 200)).put("title", "章节").put("readAt", i + 30));
        book(root).put("history", history);
        PersonalDataImporter.importFromJson(db, root.toString());
        PersonalDataImporter.importFromJson(db, root.toString());
        assertEquals(30, db.readingHistoryDao().getAllHistory().size());
    }

    @Test public void unknownFormatVersionAndOutOfRangeSettingsAreRejected() throws Exception {
        JSONObject root = backup(); root.put("format", "reader.personal.v1"); expectFailure(root.toString());
        root = backup(); root.put("version", 99); expectFailure(root.toString());
        root = backup(); root.getJSONObject("settings").put("fontSize", 100000); expectFailure(root.toString());
        root = backup(); root.getJSONObject("settings").put("fontSize", "18"); expectFailure(root.toString());
        assertEquals(18f, db.settingsDao().getSettings().fontSize, 0.001f);
    }

    @Test public void oversizedAndDeepDocumentsAreRejectedBeforeWrites() throws Exception {
        StringBuilder large = new StringBuilder();
        for (int i = 0; i <= PersonalDataImporter.MAX_BYTES; i++) large.append(' ');
        expectFailure(large.toString());
        StringBuilder deep = new StringBuilder();
        for (int i = 0; i < 40; i++) deep.append('[');
        deep.append('0'); for (int i = 0; i < 40; i++) deep.append(']');
        expectFailure(deep.toString());
        assertEquals(1, db.bookmarkDao().getAllBookmarks().size());
    }

    private JSONObject backup() throws Exception { return new JSONObject(PersonalDataExporter.exportToJson(db)); }
    private JSONObject book(JSONObject root) throws Exception { return root.getJSONObject("personal").getJSONObject("books").getJSONObject("36780"); }
    private void expectFailure(String json) throws Exception {
        try { PersonalDataImporter.importFromJson(db, json); fail("Invalid backup must be rejected"); }
        catch (IllegalArgumentException expected) { /* Transaction must have rolled back. */ }
    }
}
