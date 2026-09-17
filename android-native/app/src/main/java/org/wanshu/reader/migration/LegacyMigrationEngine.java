package org.wanshu.reader.migration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.wanshu.reader.core.text.TextBlock;
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

public class LegacyMigrationEngine {
    private final PersonalDatabase personalDb;
    private final ContentDatabase contentDb;
    private final TextBlockBuilder textBlockBuilder;

    public LegacyMigrationEngine(
            PersonalDatabase personalDb,
            ContentDatabase contentDb,
            TextBlockBuilder textBlockBuilder
    ) {
        this.personalDb = personalDb;
        this.contentDb = contentDb;
        this.textBlockBuilder = textBlockBuilder;
    }

    public void importLocalStorage(String settingsJson, String personalJson, String localBooksJson) throws Exception {
        // 1. Settings
        if (settingsJson != null && !settingsJson.trim().isEmpty()) {
            try {
                JSONObject s = new JSONObject(settingsJson);
                SettingsEntity current = personalDb.settingsDao().getSettings();
                if (current == null) {
                    current = new SettingsEntity();
                }
                if (s.has("theme")) {
                    String t = s.optString("theme", "light");
                    if ("black".equalsIgnoreCase(t)) current.theme = "oled";
                    else if ("eink".equalsIgnoreCase(t)) current.theme = "eyecare";
                    else if ("paper".equalsIgnoreCase(t)) current.theme = "sepia";
                    else current.theme = t.toLowerCase();
                }
                if (s.has("font")) {
                    String f = s.optString("font", "system");
                    if ("hei".equalsIgnoreCase(f)) current.fontFamily = "sans";
                    else current.fontFamily = f;
                }
                if (s.has("fontSize")) current.fontSize = (float) s.optDouble("fontSize", current.fontSize);
                if (s.has("lineHeight")) current.lineHeight = (float) s.optDouble("lineHeight", current.lineHeight);
                if (s.has("paraGap")) current.paragraphSpacing = (float) s.optDouble("paraGap", current.paragraphSpacing);
                if (s.has("margin")) current.pageMargin = s.optInt("margin", current.pageMargin);
                if (s.has("maxWidth")) current.maxWidthChars = s.optInt("maxWidth", current.maxWidthChars);
                if (s.has("prefetch")) current.prefetchNextChapter = s.optBoolean("prefetch", current.prefetchNextChapter);
                if (s.has("keepScreenAwake")) current.keepScreenAwake = s.optBoolean("keepScreenAwake", current.keepScreenAwake);
                if (s.has("immersiveReading")) current.immersiveMode = s.optBoolean("immersiveReading", current.immersiveMode);
                if (s.has("paperTexture")) current.paperTextureEnabled = s.optBoolean("paperTexture", current.paperTextureEnabled);

                personalDb.settingsDao().saveSettings(current);
            } catch (Exception ignored) {
            }
        }

        // 2. Personal data (progress, bookmarks, history)
        if (personalJson != null && !personalJson.trim().isEmpty()) {
            try {
                JSONObject root = new JSONObject(personalJson);
                JSONObject books = root.optJSONObject("books");
                if (books != null) {
                    Iterator<String> it = books.keys();
                    while (it.hasNext()) {
                        String bookId = it.next();
                        JSONObject bObj = books.optJSONObject(bookId);
                        if (bObj == null) continue;

                        // Progress
                        JSONObject prog = bObj.optJSONObject("progress");
                        if (prog != null) {
                            String chapterId = prog.optString("chapterId", "");
                            int paragraph = prog.optInt("paragraph", 0);
                            int offset = prog.optInt("offset", 0);
                            long updatedAt = prog.optLong("updatedAt", 0L);

                            ReadingProgressEntity current = personalDb.readingProgressDao().getProgress(bookId);
                            // Safety invariant: NEVER overwrite newer native progress with older legacy progress
                            if (current == null || current.updatedAt < updatedAt) {
                                ReadingProgressEntity entity = new ReadingProgressEntity();
                                entity.bookId = bookId;
                                entity.chapterId = chapterId;
                                entity.paragraphIndex = paragraph;
                                entity.offsetUtf16 = offset;
                                entity.updatedAt = updatedAt > 0 ? updatedAt : System.currentTimeMillis();
                                entity.sequenceNumber = current != null ? current.sequenceNumber + 1 : 1L;
                                entity.quote = prog.optString("excerpt", "");
                                personalDb.readingProgressDao().saveProgress(entity);
                            }
                        }

                        // Bookmarks
                        JSONArray bmArr = bObj.optJSONArray("bookmarks");
                        if (bmArr != null) {
                            for (int b = 0; b < bmArr.length(); b++) {
                                JSONObject bmObj = bmArr.optJSONObject(b);
                                if (bmObj == null) continue;
                                BookmarkEntity bm = new BookmarkEntity();
                                bm.id = bmObj.optString("id", java.util.UUID.randomUUID().toString());
                                bm.bookId = bookId;
                                bm.chapterId = bmObj.optString("chapterId", "");
                                bm.paragraphIndex = bmObj.optInt("paragraph", 0);
                                bm.offsetUtf16 = bmObj.optInt("offset", 0);
                                bm.snippet = bmObj.optString("excerpt", "");
                                bm.createdAt = bmObj.optLong("createdAt", System.currentTimeMillis());
                                personalDb.bookmarkDao().insertBookmark(bm);
                            }
                        }

                        // History
                        JSONArray histArr = bObj.optJSONArray("history");
                        if (histArr != null) {
                            for (int h = 0; h < histArr.length(); h++) {
                                JSONObject hObj = histArr.optJSONObject(h);
                                if (hObj == null) continue;
                                ReadingHistoryEntity he = new ReadingHistoryEntity();
                                he.bookId = bookId;
                                he.chapterId = hObj.optString("chapterId", "");
                                he.title = hObj.optString("chapterTitle", "");
                                he.readAt = hObj.optLong("at", System.currentTimeMillis());
                                personalDb.readingHistoryDao().insertHistory(he);
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // 3. Local books metadata
        if (localBooksJson != null && !localBooksJson.trim().isEmpty()) {
            try {
                JSONArray lbArr = new JSONArray(localBooksJson);
                for (int i = 0; i < lbArr.length(); i++) {
                    JSONObject lb = lbArr.optJSONObject(i);
                    if (lb == null) continue;
                    String id = lb.optString("id", "");
                    if (id.isEmpty()) continue;

                    BookEntity existing = contentDb.bookDao().getBook(id);
                    if (existing == null) {
                        BookEntity book = new BookEntity();
                        book.bookId = id;
                        book.title = lb.optString("title", "本地书");
                        book.author = "本地导入";
                        book.sourceType = "LOCAL_TXT";
                        book.chapterCount = lb.optInt("chapters", 0);
                        book.totalBytes = lb.optLong("bytes", 0L);
                        book.importStatus = "READY";
                        long importedAt = lb.optLong("importedAt", System.currentTimeMillis());
                        book.createdAt = importedAt;
                        book.updatedAt = importedAt;
                        contentDb.bookDao().insertBook(book);
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    public int importChaptersBatch(String batchJson) throws Exception {
        if (batchJson == null || batchJson.trim().isEmpty()) return 0;
        JSONArray arr = new JSONArray(batchJson);
        int count = 0;

        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj == null) continue;

            String bookId = obj.optString("bookId", "36780");
            String chapterId = obj.optString("chapterId", "");
            if (chapterId.isEmpty()) continue;

            String title = obj.optString("title", "");
            boolean complete = obj.optBoolean("complete", false);
            String source = obj.optString("source", "remote");
            String prevId = obj.isNull("prevId") ? null : obj.optString("prevId");
            String nextId = obj.isNull("nextId") ? null : obj.optString("nextId");

            JSONArray paras = obj.optJSONArray("paragraphs");
            List<String> paraList = new ArrayList<String>();
            if (paras != null) {
                for (int p = 0; p < paras.length(); p++) {
                    paraList.add(paras.optString(p, ""));
                }
            }

            ChapterEntity existing = contentDb.chapterDao().getChapter(bookId, chapterId);
            if (existing != null && existing.isComplete) {
                count++;
                continue;
            }

            ChapterEntity chapter = new ChapterEntity();
            chapter.bookId = bookId;
            chapter.chapterId = chapterId;
            chapter.title = title;
            chapter.source = "local".equalsIgnoreCase(source) ? "LOCAL" : "REMOTE";
            chapter.isComplete = complete;
            chapter.fetchedAt = obj.optLong("fetchedAt", System.currentTimeMillis());
            chapter.prevChapterId = prevId;
            chapter.nextChapterId = nextId;
            chapter.sourceSemanticsVersion = obj.optInt("v", 5);

            JSONArray missing = obj.optJSONArray("missingPages");
            chapter.missingPagesJson = missing != null ? missing.toString() : "[]";

            List<TextBlock> textBlocks;
            if (textBlockBuilder != null) {
                textBlocks = textBlockBuilder.buildBlocks(paraList);
            } else {
                textBlocks = new ArrayList<TextBlock>();
            }

            List<ChapterBlockEntity> blocks = new ArrayList<ChapterBlockEntity>();
            int totalChars = 0;
            for (int b = 0; b < textBlocks.size(); b++) {
                TextBlock tb = textBlocks.get(b);
                ChapterBlockEntity cbe = new ChapterBlockEntity();
                cbe.bookId = bookId;
                cbe.chapterId = chapterId;
                cbe.blockIndex = tb.getBlockIndex();
                cbe.startParagraphIndex = tb.getStartParagraphIndex();
                cbe.endParagraphIndex = tb.getEndParagraphIndex();
                cbe.content = tb.getText();
                blocks.add(cbe);
                totalChars += tb.getText() != null ? tb.getText().length() : 0;
            }
            chapter.charCount = totalChars > 0 ? totalChars : obj.optInt("charCount", 0);
            chapter.bytes = chapter.charCount * 2L;

            contentDb.chapterDao().insertChapter(chapter);
            contentDb.chapterBlockDao().deleteBlocks(bookId, chapterId);
            if (!blocks.isEmpty()) {
                contentDb.chapterBlockDao().insertBlocks(blocks);
            }
            count++;
        }
        return count;
    }

    public int importTocBatch(String batchJson) throws Exception {
        if (batchJson == null || batchJson.trim().isEmpty()) return 0;
        JSONArray arr = new JSONArray(batchJson);
        int totalEntries = 0;

        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj == null) continue;

            String bookId = obj.optString("bookId", "36780");
            long updatedAt = obj.optLong("updatedAt", System.currentTimeMillis());
            JSONObject pagesObj = obj.optJSONObject("pages");
            if (pagesObj == null) continue;

            Iterator<String> pageKeys = pagesObj.keys();
            while (pageKeys.hasNext()) {
                String key = pageKeys.next();
                int pageIndex;
                try {
                    pageIndex = Integer.parseInt(key);
                } catch (NumberFormatException e) {
                    continue;
                }

                TocPageEntity pe = new TocPageEntity();
                pe.bookId = bookId;
                pe.pageIndex = pageIndex;
                pe.status = "LOADED";
                pe.error = "";
                pe.updatedAt = updatedAt;
                contentDb.tocDao().insertTocPages(Collections.singletonList(pe));

                JSONArray entriesArr = pagesObj.optJSONArray(key);
                if (entriesArr != null) {
                    List<TocEntryEntity> entries = new ArrayList<TocEntryEntity>();
                    for (int e = 0; e < entriesArr.length(); e++) {
                        JSONObject item = entriesArr.optJSONObject(e);
                        if (item == null) continue;

                        TocEntryEntity te = new TocEntryEntity();
                        te.bookId = bookId;
                        te.chapterId = item.optString("chapterId", "");
                        te.title = item.optString("title", "");
                        te.isExtra = item.optBoolean("isExtra", false);
                        te.sourcePageIndex = pageIndex;
                        te.orderKey = (long) pageIndex * 10000L + item.optInt("orderIndex", e);
                        entries.add(te);
                    }
                    if (!entries.isEmpty()) {
                        contentDb.tocDao().insertEntries(entries);
                        totalEntries += entries.size();
                    }
                }
            }
        }
        return totalEntries;
    }

    public void completeMigration(String runId, String summaryJson) {
        MigrationRunEntity run = new MigrationRunEntity();
        run.source = "legacy_webview";
        run.phase = "COMPLETE";
        run.checkpoint = "completed";
        run.summary = summaryJson != null ? summaryJson : "";
        run.error = "";
        run.createdAt = System.currentTimeMillis();
        personalDb.migrationRunDao().insertRun(run);
    }

    public void recordFailure(String runId, String errorStage, String errorMessage) {
        MigrationRunEntity run = new MigrationRunEntity();
        run.source = "legacy_webview";
        run.phase = "FAILED";
        run.checkpoint = errorStage != null ? errorStage : "unknown";
        run.summary = "";
        run.error = errorMessage != null ? errorMessage : "";
        run.createdAt = System.currentTimeMillis();
        personalDb.migrationRunDao().insertRun(run);
    }
}
