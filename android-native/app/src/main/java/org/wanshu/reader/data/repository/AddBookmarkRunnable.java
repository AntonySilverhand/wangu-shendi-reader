package org.wanshu.reader.data.repository;

import java.util.UUID;
import org.wanshu.reader.core.model.ReadingAnchor;
import org.wanshu.reader.data.personal.PersonalDatabase;
import org.wanshu.reader.data.personal.entity.BookmarkEntity;

public class AddBookmarkRunnable implements Runnable {
    private final PersonalDatabase db;
    private final ReadingAnchor anchor;
    private final String snippet;
    private final DataCallback<BookmarkEntity> callback;

    public AddBookmarkRunnable(
            PersonalDatabase db,
            ReadingAnchor anchor,
            String snippet,
            DataCallback<BookmarkEntity> callback
    ) {
        this.db = db;
        this.anchor = anchor;
        this.snippet = snippet != null ? snippet : "";
        this.callback = callback;
    }

    @Override
    public void run() {
        try {
            java.util.List<BookmarkEntity> existing = db.bookmarkDao().getBookmarks(anchor.getBookId());
            BookmarkEntity target = null;
            if (existing != null) {
                for (int i = 0; i < existing.size(); i++) {
                    BookmarkEntity b = existing.get(i);
                    if (anchor.getChapterId().equals(b.chapterId)
                            && Math.abs(b.paragraphIndex - anchor.getParagraphIndex()) <= 1) {
                        target = b;
                        break;
                    }
                }
            }

            if (target != null) {
                target.paragraphIndex = anchor.getParagraphIndex();
                target.offsetUtf16 = anchor.getOffsetUtf16();
                target.snippet = snippet;
                target.createdAt = System.currentTimeMillis();
                db.bookmarkDao().insertBookmark(target);
                if (callback != null) {
                    callback.onSuccess(target);
                }
            } else {
                BookmarkEntity entity = new BookmarkEntity();
                entity.id = UUID.randomUUID().toString();
                entity.bookId = anchor.getBookId();
                entity.chapterId = anchor.getChapterId();
                entity.paragraphIndex = anchor.getParagraphIndex();
                entity.offsetUtf16 = anchor.getOffsetUtf16();
                entity.snippet = snippet;
                entity.createdAt = System.currentTimeMillis();

                db.bookmarkDao().insertBookmark(entity);

                if (callback != null) {
                    callback.onSuccess(entity);
                }
            }
        } catch (Throwable t) {
            if (callback != null) {
                callback.onError(t);
            }
        }
    }
}
