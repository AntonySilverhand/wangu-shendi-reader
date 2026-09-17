package org.wanshu.reader.data.repository;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.wanshu.reader.core.model.ChapterResult;
import org.wanshu.reader.core.text.TextBlock;
import org.wanshu.reader.core.text.TextBlockBuilder;
import org.wanshu.reader.data.content.ContentDatabase;
import org.wanshu.reader.data.content.entity.ChapterBlockEntity;
import org.wanshu.reader.data.content.entity.ChapterEntity;
import org.wanshu.reader.data.content.entity.DownloadTaskEntity;

public class SaveChapterRunnable implements Runnable {
    private final ContentDatabase db;
    private final ChapterResult result;
    private final String source;
    private final TextBlockBuilder textBlockBuilder;
    private final CompletionCallback callback;

    public SaveChapterRunnable(
            ContentDatabase db,
            ChapterResult result,
            String source,
            TextBlockBuilder textBlockBuilder,
            CompletionCallback callback
    ) {
        this.db = db;
        this.result = result;
        this.source = source != null ? source : "REMOTE";
        this.textBlockBuilder = textBlockBuilder;
        this.callback = callback;
    }

    @Override
    public void run() {
        try {
            final String bookId = result.getBookId();
            final String chapterId = result.getChapterId();

            // 1. Check existing chapter in DB: do not overwrite with shorter/incomplete content
            ChapterEntity existing = db.chapterDao().getChapter(bookId, chapterId);
            if (existing != null) {
                // If existing content has more characters or paragraphs, do not overwrite with shorter
                if (existing.charCount > result.getCharCount() && result.getCharCount() > 0) {
                    if (callback != null) {
                        callback.onSuccess();
                    }
                    return;
                }
            }

            // 2. Prepare blocks
            List<TextBlock> blocks = textBlockBuilder.buildBlocks(result.getParagraphs());
            long totalBytes = 0L;
            List<ChapterBlockEntity> blockEntities = new ArrayList<ChapterBlockEntity>();

            for (int i = 0; i < blocks.size(); i++) {
                TextBlock b = blocks.get(i);
                ChapterBlockEntity be = new ChapterBlockEntity();
                be.bookId = bookId;
                be.chapterId = chapterId;
                be.blockIndex = b.getBlockIndex();
                be.startParagraphIndex = b.getStartParagraphIndex();
                be.endParagraphIndex = b.getEndParagraphIndex();
                be.content = b.getText();
                totalBytes += b.getText().getBytes(StandardCharsets.UTF_8).length;
                blockEntities.add(be);
            }

            // 3. Prepare chapter entity
            ChapterEntity entity = new ChapterEntity();
            entity.bookId = bookId;
            entity.chapterId = chapterId;
            entity.title = result.getTitle();
            entity.source = source;
            entity.isComplete = result.isComplete();
            entity.sourceSemanticsVersion = result.getSourceSemanticsVersion();
            entity.charCount = result.getCharCount();
            entity.bytes = totalBytes;
            entity.prevChapterId = result.getPrevChapterId();
            entity.nextChapterId = result.getNextChapterId();
            entity.revision = System.currentTimeMillis();
            entity.fetchedAt = System.currentTimeMillis();

            // 4. Save in transaction
            db.runInTransaction(new SaveChapterTransactionRunnable(
                    db,
                    entity,
                    blockEntities,
                    result.isComplete()
            ));

            if (callback != null) {
                callback.onSuccess();
            }
        } catch (Throwable t) {
            if (callback != null) {
                callback.onError(t);
            }
        }
    }
}
