package org.wanshu.reader.data.export;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;
import org.wanshu.reader.data.personal.PersonalDatabase;
import org.wanshu.reader.data.personal.entity.BookmarkEntity;
import org.wanshu.reader.data.personal.entity.ReadingHistoryEntity;
import org.wanshu.reader.data.personal.entity.ReadingProgressEntity;
import org.wanshu.reader.data.personal.entity.SettingsEntity;

/** Native personal backups only. Any validation/write failure rolls back the entire import. */
public final class PersonalDataImporter {
    public static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final int MAX_RECORDS = 10000;

    public static int importFromJson(PersonalDatabase db, String json) throws Exception {
        if (json == null || json.isEmpty() || json.length() > MAX_BYTES) throw new IllegalArgumentException("备份为空或过大");
        checkDepth(json);
        JSONObject root = new JSONObject(json);
        if (!PersonalDataExporter.FORMAT.equals(root.optString("format")) || integer(root, "version", 1, 1) != 1)
            throw new IllegalArgumentException("仅支持原生阅读器 JSON 备份；旧网页版备份暂不支持");
        JSONObject personal = root.getJSONObject("personal");
        integer(personal, "version", 1, 1);
        JSONObject books = personal.getJSONObject("books");
        if (books.length() > 1000) throw new IllegalArgumentException("备份中的书籍过多");
        SettingsEntity settings = settings(root.getJSONObject("settings"));
        int count = 1, records = 0;
        db.beginTransaction();
        try {
            db.settingsDao().saveSettings(settings);
            Iterator<String> keys = books.keys();
            while (keys.hasNext()) {
                String bookId = key(keys.next());
                JSONObject book = books.getJSONObject(bookId);
                if (book.has("progress")) {
                    JSONObject value = book.getJSONObject("progress");
                    ReadingProgressEntity incoming = new ReadingProgressEntity();
                    incoming.bookId = bookId; incoming.chapterId = key(text(value, "chapterId", 200));
                    incoming.paragraphIndex = integer(value, "paragraphIndex", 0, Integer.MAX_VALUE);
                    incoming.offsetUtf16 = integer(value, "offsetUtf16", 0, Integer.MAX_VALUE);
                    incoming.updatedAt = timestamp(value, "updatedAt");
                    incoming.paragraphHash = text(value, "paragraphHash", 200);
                    incoming.quote = text(value, "quote", 2000);
                    incoming.contentRevision = timestamp(value, "contentRevision");
                    ReadingProgressEntity current = db.readingProgressDao().getProgress(bookId);
                    if (current == null || incoming.updatedAt > current.updatedAt) {
                        // Do not inject wall-clock sequence numbers: subsequent queued reader saves must still work.
                        incoming.sequenceNumber = current == null ? 0 : current.sequenceNumber;
                        db.readingProgressDao().saveProgress(incoming); count++;
                    }
                }
                JSONArray bookmarks = book.optJSONArray("bookmarks");
                if (book.has("bookmarks") && bookmarks == null) throw new IllegalArgumentException("书签格式错误");
                if (bookmarks != null) {
                    records += bookmarks.length(); checkCount(records);
                    Set<String> locations = new HashSet<String>();
                    for (BookmarkEntity existing : db.bookmarkDao().getBookmarks(bookId)) locations.add(location(existing));
                    for (int i = 0; i < bookmarks.length(); i++) {
                        JSONObject value = bookmarks.getJSONObject(i);
                        BookmarkEntity bookmark = new BookmarkEntity();
                        bookmark.id = key(text(value, "id", 200)); bookmark.bookId = bookId;
                        bookmark.chapterId = key(text(value, "chapterId", 200));
                        bookmark.paragraphIndex = integer(value, "paragraphIndex", 0, Integer.MAX_VALUE);
                        bookmark.offsetUtf16 = integer(value, "offsetUtf16", 0, Integer.MAX_VALUE);
                        bookmark.snippet = text(value, "snippet", 2000);
                        bookmark.createdAt = timestamp(value, "createdAt");
                        if (locations.add(location(bookmark))) {
                            if (db.bookmarkDao().getBookmark(bookmark.id) != null) bookmark.id = UUID.randomUUID().toString();
                            db.bookmarkDao().insertBookmark(bookmark); count++;
                        }
                    }
                }
                JSONArray history = book.optJSONArray("history");
                if (book.has("history") && history == null) throw new IllegalArgumentException("历史记录格式错误");
                if (history != null) {
                    records += history.length(); checkCount(records);
                    Map<String, ReadingHistoryEntity> existing = new HashMap<String, ReadingHistoryEntity>();
                    for (ReadingHistoryEntity entry : db.readingHistoryDao().getHistoryForBook(bookId)) existing.put(entry.chapterId, entry);
                    for (int i = 0; i < history.length(); i++) {
                        JSONObject value = history.getJSONObject(i);
                        ReadingHistoryEntity entry = new ReadingHistoryEntity();
                        entry.bookId = bookId; entry.chapterId = key(text(value, "chapterId", 200));
                        entry.title = text(value, "title", 500); entry.readAt = timestamp(value, "readAt");
                        ReadingHistoryEntity current = existing.get(entry.chapterId);
                        if (current == null || entry.readAt > current.readAt) {
                            db.readingHistoryDao().deleteEntry(bookId, entry.chapterId);
                            db.readingHistoryDao().insertHistory(entry); existing.put(entry.chapterId, entry); count++;
                        }
                    }
                    db.readingHistoryDao().trimHistory(bookId);
                }
            }
            db.setTransactionSuccessful();
            return count;
        } finally { db.endTransaction(); }
    }

    private static void checkDepth(String json) {
        int depth = 0;
        boolean quoted = false, escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
            } else if (c == '"') quoted = true;
            else if (c == '{' || c == '[') {
                if (++depth > 32) throw new IllegalArgumentException("JSON 层级过深");
            } else if (c == '}' || c == ']') depth--;
        }
    }

    private static SettingsEntity settings(JSONObject value) throws Exception {
        SettingsEntity settings = new SettingsEntity();
        settings.theme = text(value, "theme", 20);
        if (!java.util.Arrays.asList("light", "dark", "sepia", "eyecare", "oled").contains(settings.theme))
            throw new IllegalArgumentException("未知主题");
        settings.fontFamily = text(value, "fontFamily", 80);
        settings.fontSize = (float) number(value, "fontSize", 14, 28);
        settings.lineHeight = (float) number(value, "lineHeight", 1.3f, 2.4f);
        settings.paragraphSpacing = (float) number(value, "paragraphSpacing", 0, 3);
        settings.pageMargin = integer(value, "pageMargin", 8, 64);
        settings.maxWidthChars = integer(value, "maxWidthChars", 16, 80);
        settings.maxCacheMb = integer(value, "maxCacheMb", 32, 4096);
        settings.prefetchNextChapter = bool(value, "prefetchNextChapter");
        settings.keepScreenAwake = bool(value, "keepScreenAwake");
        settings.immersiveMode = bool(value, "immersiveMode");
        settings.paperTextureEnabled = bool(value, "paperTextureEnabled");
        settings.autoCacheScope = text(value, "autoCacheScope", 40);
        if (!java.util.Arrays.asList("ENTIRE_BOOK", "NEXT_50", "NEXT_200").contains(settings.autoCacheScope))
            throw new IllegalArgumentException("未知自动缓存范围");
        // Restoring a file is not consent to start a whole-book or metered download.
        settings.autoCacheEnabled = false; settings.autoCacheMetered = false;
        return settings;
    }
    private static String location(BookmarkEntity bookmark) {
        return bookmark.chapterId + ":" + bookmark.paragraphIndex + ":" + bookmark.offsetUtf16;
    }
    private static void checkCount(int count) {
        if (count > MAX_RECORDS) throw new IllegalArgumentException("备份记录过多");
    }
    private static String key(String value) {
        if (value.isEmpty() || value.length() > 200 || !value.matches("[A-Za-z0-9._:-]+")) throw new IllegalArgumentException("书籍或章节标识无效");
        return value;
    }
    private static String text(JSONObject object, String name, int limit) throws Exception {
        Object value = object.get(name);
        if (!(value instanceof String) || ((String) value).length() > limit) throw new IllegalArgumentException(name + " 格式错误");
        return (String) value;
    }
    private static boolean bool(JSONObject object, String name) throws Exception {
        Object value = object.get(name);
        if (!(value instanceof Boolean)) throw new IllegalArgumentException(name + " 格式错误");
        return (Boolean) value;
    }
    private static double number(JSONObject object, String name, double min, double max) throws Exception {
        Object value = object.get(name);
        if (!(value instanceof Number)) throw new IllegalArgumentException(name + " 必须为数字");
        double number = ((Number) value).doubleValue();
        if (!Double.isFinite(number) || number < min || number > max) throw new IllegalArgumentException(name + " 超出范围");
        return number;
    }
    private static int integer(JSONObject object, String name, int min, int max) throws Exception {
        double number = number(object, name, min, max);
        if (number != Math.rint(number)) throw new IllegalArgumentException(name + " 必须为整数");
        return (int) number;
    }
    private static long timestamp(JSONObject object, String name) throws Exception {
        double number = number(object, name, 0, Long.MAX_VALUE);
        if (number != Math.rint(number)) throw new IllegalArgumentException(name + " 必须为整数");
        return object.getLong(name);
    }
}
