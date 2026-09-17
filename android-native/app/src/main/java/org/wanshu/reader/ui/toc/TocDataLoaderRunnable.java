package org.wanshu.reader.ui.toc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import org.wanshu.reader.data.content.ContentDatabase;
import org.wanshu.reader.data.content.entity.TocEntryEntity;

public class TocDataLoaderRunnable implements Runnable {
    private final String bookId;
    private final String currentChapterId;
    private final ContentDatabase db;
    private final Executor mainExecutor;
    private final TocDialog dialog;

    public TocDataLoaderRunnable(
            String bookId,
            String currentChapterId,
            ContentDatabase db,
            Executor mainExecutor,
            TocDialog dialog
    ) {
        this.bookId = bookId != null ? bookId : "";
        this.currentChapterId = currentChapterId != null ? currentChapterId : "";
        this.db = db;
        this.mainExecutor = mainExecutor;
        this.dialog = dialog;
    }

    @Override
    public void run() {
        List<TocEntryEntity> entities = db.tocDao().getEntries(bookId);
        List<String> completed = db.chapterDao().getCompletedChapterIds(bookId);
        List<String> allCached = db.chapterDao().getAllCachedChapterIds(bookId);

        Set<String> completedSet = new HashSet<String>();
        if (completed != null) {
            completedSet.addAll(completed);
        }

        Set<String> cachedSet = new HashSet<String>();
        if (allCached != null) {
            cachedSet.addAll(allCached);
        }

        List<TocItemModel> items = new ArrayList<TocItemModel>();
        if (entities != null) {
            for (int i = 0; i < entities.size(); i++) {
                TocEntryEntity e = entities.get(i);
                boolean isCurrent = currentChapterId.equals(e.chapterId);
                boolean isComplete = completedSet.contains(e.chapterId);
                boolean isPartial = !isComplete && cachedSet.contains(e.chapterId);

                items.add(new TocItemModel(
                        e.chapterId,
                        e.title,
                        e.orderKey,
                        e.isExtra,
                        isCurrent,
                        isComplete,
                        isPartial
                ));
            }
        }

        if (mainExecutor != null) {
            mainExecutor.execute(new TocDataLoadedRunnable(dialog, items));
        }
    }
}
