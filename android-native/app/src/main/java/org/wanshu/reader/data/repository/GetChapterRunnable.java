package org.wanshu.reader.data.repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.wanshu.reader.core.model.ChapterResult;
import org.wanshu.reader.data.content.ContentDatabase;
import org.wanshu.reader.data.content.entity.ChapterBlockEntity;
import org.wanshu.reader.data.content.entity.ChapterEntity;

public class GetChapterRunnable implements Runnable {
    private final ContentDatabase db;
    private final String bookId;
    private final String chapterId;
    private final DataCallback<ChapterResult> callback;

    public GetChapterRunnable(
            ContentDatabase db,
            String bookId,
            String chapterId,
            DataCallback<ChapterResult> callback
    ) {
        this.db = db;
        this.bookId = bookId;
        this.chapterId = chapterId;
        this.callback = callback;
    }

    @Override
    public void run() {
        try {
            ChapterEntity entity = db.chapterDao().getChapter(bookId, chapterId);
            if (entity == null) {
                if (callback != null) {
                    callback.onSuccess(null);
                }
                return;
            }

            List<ChapterBlockEntity> blockEntities = db.chapterBlockDao().getBlocks(bookId, chapterId);
            List<String> paragraphs = new ArrayList<String>();

            for (int i = 0; i < blockEntities.size(); i++) {
                String content = blockEntities.get(i).content;
                if (content != null && !content.isEmpty()) {
                    String[] split = content.split("\n\n");
                    paragraphs.addAll(Arrays.asList(split));
                }
            }

            ChapterResult result = new ChapterResult(
                    entity.bookId,
                    entity.chapterId,
                    entity.title,
                    paragraphs,
                    entity.prevChapterId,
                    entity.nextChapterId,
                    blockEntities.size(),
                    entity.charCount,
                    entity.isComplete,
                    new ArrayList<Integer>(),
                    entity.sourceSemanticsVersion
            );

            if (callback != null) {
                callback.onSuccess(result);
            }
        } catch (Throwable t) {
            if (callback != null) {
                callback.onError(t);
            }
        }
    }
}
