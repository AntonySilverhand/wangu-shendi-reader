package org.wanshu.reader.data.export;

import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.wanshu.reader.data.personal.PersonalDatabase;
import org.wanshu.reader.data.personal.entity.BookmarkEntity;
import org.wanshu.reader.data.personal.entity.ReadingHistoryEntity;
import org.wanshu.reader.data.personal.entity.ReadingProgressEntity;
import org.wanshu.reader.data.personal.entity.SettingsEntity;

public class PersonalDataExporter {
    public static final String FORMAT = "wangu-reader-native-personal";

    public static String exportToJson(PersonalDatabase db) throws Exception {
        db.beginTransaction();
        try {
            String json = snapshot(db);
            db.setTransactionSuccessful();
            return json;
        } finally { db.endTransaction(); }
    }

    private static String snapshot(PersonalDatabase db) throws Exception {
        JSONObject root = new JSONObject();
        root.put("app", "wangu-shendi-reader");
        root.put("format", FORMAT);
        root.put("version", 1);
        root.put("exportedAt", System.currentTimeMillis());

        // 1. Settings
        SettingsEntity settings = db.settingsDao().getSettings();
        if (settings == null) settings = new SettingsEntity();
        JSONObject sObj = new JSONObject();
        sObj.put("theme", settings.theme);
        sObj.put("fontFamily", settings.fontFamily);
        sObj.put("paragraphSpacing", (double) settings.paragraphSpacing);
        sObj.put("maxCacheMb", settings.maxCacheMb);
        sObj.put("paperTextureEnabled", settings.paperTextureEnabled);
        sObj.put("fontSize", (double) settings.fontSize);
        sObj.put("lineHeight", (double) settings.lineHeight);
        sObj.put("pageMargin", settings.pageMargin);
        sObj.put("maxWidthChars", settings.maxWidthChars);
        sObj.put("prefetchNextChapter", settings.prefetchNextChapter);
        sObj.put("keepScreenAwake", settings.keepScreenAwake);
        sObj.put("immersiveMode", settings.immersiveMode);
        sObj.put("autoCacheEnabled", settings.autoCacheEnabled);
        sObj.put("autoCacheScope", settings.autoCacheScope);
        sObj.put("autoCacheMetered", settings.autoCacheMetered);
        root.put("settings", sObj);

        // 2. Personal
        JSONObject personalObj = new JSONObject();
        personalObj.put("version", 1);
        JSONObject booksObj = new JSONObject();

        List<ReadingProgressEntity> progressList = db.readingProgressDao().getAllProgress();
        for (int i = 0; i < progressList.size(); i++) {
            ReadingProgressEntity p = progressList.get(i);
            JSONObject bObj = booksObj.optJSONObject(p.bookId);
            if (bObj == null) {
                bObj = new JSONObject();
                booksObj.put(p.bookId, bObj);
            }
            JSONObject prog = new JSONObject();
            prog.put("chapterId", p.chapterId);
            prog.put("paragraphIndex", p.paragraphIndex);
            prog.put("offsetUtf16", p.offsetUtf16);
            prog.put("updatedAt", p.updatedAt);
            prog.put("paragraphHash", p.paragraphHash);
            prog.put("quote", p.quote);
            prog.put("contentRevision", p.contentRevision);
            bObj.put("progress", prog);
        }

        List<BookmarkEntity> bookmarks = db.bookmarkDao().getAllBookmarks();
        for (int i = 0; i < bookmarks.size(); i++) {
            BookmarkEntity bm = bookmarks.get(i);
            JSONObject bObj = booksObj.optJSONObject(bm.bookId);
            if (bObj == null) {
                bObj = new JSONObject();
                booksObj.put(bm.bookId, bObj);
            }
            JSONArray bmArr = bObj.optJSONArray("bookmarks");
            if (bmArr == null) {
                bmArr = new JSONArray();
                bObj.put("bookmarks", bmArr);
            }
            JSONObject bmItem = new JSONObject();
            bmItem.put("id", bm.id);
            bmItem.put("chapterId", bm.chapterId);
            bmItem.put("paragraphIndex", bm.paragraphIndex);
            bmItem.put("offsetUtf16", bm.offsetUtf16);
            bmItem.put("snippet", bm.snippet);
            bmItem.put("createdAt", bm.createdAt);
            bmArr.put(bmItem);
        }

        List<ReadingHistoryEntity> history = db.readingHistoryDao().getAllHistory();
        for (int i = 0; i < history.size(); i++) {
            ReadingHistoryEntity h = history.get(i);
            JSONObject bObj = booksObj.optJSONObject(h.bookId);
            if (bObj == null) {
                bObj = new JSONObject();
                booksObj.put(h.bookId, bObj);
            }
            JSONArray hArr = bObj.optJSONArray("history");
            if (hArr == null) {
                hArr = new JSONArray();
                bObj.put("history", hArr);
            }
            JSONObject hItem = new JSONObject();
            hItem.put("chapterId", h.chapterId);
            hItem.put("title", h.title);
            hItem.put("readAt", h.readAt);
            hArr.put(hItem);
        }

        if (booksObj.length() > 1000 || bookmarks.size() + history.size() > 10000)
            throw new IllegalArgumentException("个人记录超过备份上限，请先整理书签与历史");
        personalObj.put("books", booksObj);
        root.put("personal", personalObj);

        return root.toString(2);
    }
}
