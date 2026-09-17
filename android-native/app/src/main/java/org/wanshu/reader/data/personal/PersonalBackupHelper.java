package org.wanshu.reader.data.personal;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import org.json.JSONArray;
import org.json.JSONObject;
import org.wanshu.reader.data.personal.entity.BookmarkEntity;
import org.wanshu.reader.data.personal.entity.ReadingHistoryEntity;
import org.wanshu.reader.data.personal.entity.ReadingProgressEntity;
import org.wanshu.reader.data.personal.entity.SettingsEntity;

public class PersonalBackupHelper {
    private static final String APP_ID = "wangu-shendi-reader";
    private static final int FORMAT_VERSION = 1;

    public static String exportToJson(
            SettingsEntity settings,
            List<ReadingProgressEntity> progressList,
            List<BookmarkEntity> bookmarks,
            List<ReadingHistoryEntity> history
    ) throws Exception {
        JSONObject root = new JSONObject();
        root.put("app", APP_ID);
        root.put("version", FORMAT_VERSION);

        SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        root.put("exportedAt", isoFormat.format(new Date()));

        // Settings
        if (settings != null) {
            JSONObject s = new JSONObject();
            s.put("theme", settings.theme);
            s.put("fontFamily", settings.fontFamily);
            s.put("fontSize", (double) settings.fontSize);
            s.put("lineHeight", (double) settings.lineHeight);
            s.put("paragraphSpacing", (double) settings.paragraphSpacing);
            s.put("pageMargin", settings.pageMargin);
            s.put("maxWidthChars", settings.maxWidthChars);
            s.put("prefetchNextChapter", settings.prefetchNextChapter);
            s.put("keepScreenAwake", settings.keepScreenAwake);
            s.put("immersiveMode", settings.immersiveMode);
            s.put("autoCacheEnabled", settings.autoCacheEnabled);
            s.put("autoCacheScope", settings.autoCacheScope);
            s.put("autoCacheMetered", settings.autoCacheMetered);
            s.put("maxCacheMb", settings.maxCacheMb);
            s.put("paperTextureEnabled", settings.paperTextureEnabled);
            root.put("settings", s);
        }

        // Personal data
        JSONObject personalObj = new JSONObject();
        personalObj.put("version", 1);
        JSONObject booksObj = new JSONObject();

        // Progress
        if (progressList != null) {
            for (int i = 0; i < progressList.size(); i++) {
                ReadingProgressEntity p = progressList.get(i);
                if (p.bookId == null || p.bookId.isEmpty()) continue;
                JSONObject bObj = booksObj.optJSONObject(p.bookId);
                if (bObj == null) {
                    bObj = new JSONObject();
                    bObj.put("bookmarks", new JSONArray());
                    bObj.put("history", new JSONArray());
                    booksObj.put(p.bookId, bObj);
                }
                JSONObject prog = new JSONObject();
                prog.put("chapterId", p.chapterId);
                prog.put("paragraphIndex", p.paragraphIndex);
                prog.put("offsetUtf16", p.offsetUtf16);
                prog.put("updatedAt", p.updatedAt);
                bObj.put("progress", prog);
            }
        }

        // Bookmarks
        if (bookmarks != null) {
            for (int i = 0; i < bookmarks.size(); i++) {
                BookmarkEntity bm = bookmarks.get(i);
                if (bm.bookId == null || bm.bookId.isEmpty()) continue;
                JSONObject bObj = booksObj.optJSONObject(bm.bookId);
                if (bObj == null) {
                    bObj = new JSONObject();
                    bObj.put("bookmarks", new JSONArray());
                    bObj.put("history", new JSONArray());
                    booksObj.put(bm.bookId, bObj);
                }
                JSONArray bmArr = bObj.getJSONArray("bookmarks");
                JSONObject bmObj = new JSONObject();
                bmObj.put("id", bm.id);
                bmObj.put("chapterId", bm.chapterId);
                bmObj.put("paragraphIndex", bm.paragraphIndex);
                bmObj.put("offsetUtf16", bm.offsetUtf16);
                bmObj.put("snippet", bm.snippet);
                bmObj.put("createdAt", bm.createdAt);
                bmArr.put(bmObj);
            }
        }

        // History
        if (history != null) {
            for (int i = 0; i < history.size(); i++) {
                ReadingHistoryEntity h = history.get(i);
                if (h.bookId == null || h.bookId.isEmpty()) continue;
                JSONObject bObj = booksObj.optJSONObject(h.bookId);
                if (bObj == null) {
                    bObj = new JSONObject();
                    bObj.put("bookmarks", new JSONArray());
                    bObj.put("history", new JSONArray());
                    booksObj.put(h.bookId, bObj);
                }
                JSONArray histArr = bObj.getJSONArray("history");
                JSONObject histObj = new JSONObject();
                histObj.put("chapterId", h.chapterId);
                histObj.put("title", h.title);
                histObj.put("at", h.readAt);
                histArr.put(histObj);
            }
        }

        personalObj.put("books", booksObj);
        root.put("personal", personalObj);

        return root.toString(2);
    }

    public static boolean importFromJson(String json, PersonalDatabase db, boolean replace) {
        if (json == null || json.trim().isEmpty() || db == null) {
            return false;
        }

        try {
            JSONObject root = new JSONObject(json);

            // 1. Import Settings if present
            if (root.has("settings")) {
                JSONObject s = root.getJSONObject("settings");
                SettingsEntity current = db.settingsDao().getSettings();
                if (current == null) {
                    current = new SettingsEntity();
                }
                if (s.has("theme")) current.theme = s.getString("theme");
                if (s.has("fontSize")) current.fontSize = (float) s.getDouble("fontSize");
                if (s.has("lineHeight")) current.lineHeight = (float) s.getDouble("lineHeight");
                if (s.has("pageMargin")) current.pageMargin = s.getInt("pageMargin");
                if (s.has("keepScreenAwake")) current.keepScreenAwake = s.getBoolean("keepScreenAwake");
                if (s.has("immersiveMode")) current.immersiveMode = s.getBoolean("immersiveMode");
                if (s.has("prefetchNextChapter")) current.prefetchNextChapter = s.getBoolean("prefetchNextChapter");
                if (s.has("autoCacheEnabled")) current.autoCacheEnabled = s.getBoolean("autoCacheEnabled");
                if (s.has("autoCacheScope")) current.autoCacheScope = s.getString("autoCacheScope");
                db.settingsDao().saveSettings(current);
            }

            // 2. Import personal books
            JSONObject personal = root.optJSONObject("personal");
            if (personal == null) {
                personal = root;
            }
            JSONObject books = personal.optJSONObject("books");
            if (books != null) {
                Iterator<String> keys = books.keys();
                while (keys.hasNext()) {
                    String bookId = keys.next();
                    JSONObject bObj = books.getJSONObject(bookId);

                    if (replace) {
                        db.bookmarkDao().clearBookmarks(bookId);
                    }

                    // Bookmarks
                    JSONArray bmArr = bObj.optJSONArray("bookmarks");
                    if (bmArr != null) {
                        for (int i = 0; i < bmArr.length(); i++) {
                            JSONObject bm = bmArr.getJSONObject(i);
                            BookmarkEntity entity = new BookmarkEntity();
                            entity.id = bm.optString("id", java.util.UUID.randomUUID().toString());
                            entity.bookId = bookId;
                            entity.chapterId = bm.optString("chapterId", "1");
                            entity.paragraphIndex = bm.optInt("paragraphIndex", 0);
                            entity.offsetUtf16 = bm.optInt("offsetUtf16", 0);
                            entity.snippet = bm.optString("snippet", "");
                            entity.createdAt = bm.optLong("createdAt", System.currentTimeMillis());
                            db.bookmarkDao().insertBookmark(entity);
                        }
                    }

                    // Progress
                    JSONObject prog = bObj.optJSONObject("progress");
                    if (prog != null) {
                        ReadingProgressEntity p = new ReadingProgressEntity();
                        p.bookId = bookId;
                        p.chapterId = prog.optString("chapterId", "1");
                        p.paragraphIndex = prog.optInt("paragraphIndex", 0);
                        p.offsetUtf16 = prog.optInt("offsetUtf16", 0);
                        p.updatedAt = prog.optLong("updatedAt", System.currentTimeMillis());
                        p.sequenceNumber = System.currentTimeMillis();
                        db.readingProgressDao().saveProgress(p);
                    }
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
