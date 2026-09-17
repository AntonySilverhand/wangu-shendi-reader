package org.wanshu.reader.data.repository;

import java.util.List;
import org.wanshu.reader.data.content.ContentDatabase;
import org.wanshu.reader.data.content.entity.ChapterBlockEntity;
import org.wanshu.reader.data.content.entity.ChapterEntity;
import org.wanshu.reader.data.content.entity.DownloadTaskEntity;

public class SaveChapterTransactionRunnable implements Runnable {
    private final ContentDatabase db;
    private final ChapterEntity chapter;
    private final List<ChapterBlockEntity> blocks;
    private final boolean isComplete;

    public SaveChapterTransactionRunnable(
            ContentDatabase db,
            ChapterEntity chapter,
            List<ChapterBlockEntity> blocks,
            boolean isComplete
    ) {
        this.db = db;
        this.chapter = chapter;
        this.blocks = blocks;
        this.isComplete = isComplete;
    }

    @Override
    public void run() {
        db.chapterDao().insertChapter(chapter);
        db.chapterBlockDao().deleteBlocks(chapter.bookId, chapter.chapterId);
        if (blocks != null && !blocks.isEmpty()) {
            db.chapterBlockDao().insertBlocks(blocks);
        }

        if (isComplete) {
            db.sourcePageDao().deletePagesForChapter(chapter.bookId, chapter.chapterId);

            DownloadTaskEntity task = db.downloadTaskDao().getTask(chapter.bookId, chapter.chapterId);
            if (task != null) {
                db.downloadTaskDao().updateTaskState(
                        chapter.bookId,
                        chapter.chapterId,
                        "COMPLETE",
                        task.attempts,
                        0L,
                        "",
                        0L
                );
            }
        }
    }
}
