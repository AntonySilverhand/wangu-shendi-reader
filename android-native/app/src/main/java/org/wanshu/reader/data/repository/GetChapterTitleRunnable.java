package org.wanshu.reader.data.repository;

import org.wanshu.reader.data.content.ContentDatabase;
import org.wanshu.reader.data.content.entity.ChapterEntity;
import org.wanshu.reader.data.content.entity.TocEntryEntity;

/** Looks up a chapter's title from the saved TOC, then from the cached chapter; empty when unknown. */
public class GetChapterTitleRunnable implements Runnable {
    private final ContentDatabase db;
    private final String bookId;
    private final String chapterId;
    private final DataCallback<String> callback;

    public GetChapterTitleRunnable(ContentDatabase db, String bookId, String chapterId, DataCallback<String> callback) {
        this.db = db;
        this.bookId = bookId;
        this.chapterId = chapterId;
        this.callback = callback;
    }

    @Override
    public void run() {
        try {
            String title = "";
            TocEntryEntity entry = db.tocDao().getEntry(bookId, chapterId);
            if (entry != null && entry.title != null) {
                title = entry.title;
            }
            if (title.isEmpty()) {
                ChapterEntity chapter = db.chapterDao().getChapter(bookId, chapterId);
                if (chapter != null && chapter.title != null) {
                    title = chapter.title;
                }
            }
            if (callback != null) {
                callback.onSuccess(title);
            }
        } catch (Throwable t) {
            if (callback != null) {
                callback.onError(t);
            }
        }
    }
}
