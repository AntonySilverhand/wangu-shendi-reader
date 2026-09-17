package org.wanshu.reader.data.txt;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.wanshu.reader.core.source.TitleInfo;
import org.wanshu.reader.core.source.TitleNormalizer;
import org.wanshu.reader.core.text.TextBlock;
import org.wanshu.reader.core.text.TextBlockBuilder;
import org.wanshu.reader.core.txt.RawTxtChapter;
import org.wanshu.reader.core.txt.TxtChapterConsumer;
import org.wanshu.reader.data.content.ContentDatabase;
import org.wanshu.reader.data.content.entity.ChapterBlockEntity;
import org.wanshu.reader.data.content.entity.ChapterEntity;
import org.wanshu.reader.data.content.entity.TocEntryEntity;

public class TxtImportConsumer implements TxtChapterConsumer {

    private final String bookId;
    private final ContentDatabase db;
    private final TextBlockBuilder textBlockBuilder;
    private final TxtImportListener listener;

    private int count = 0;
    private long totalBytes = 0L;
    private final List<TocEntryEntity> tocEntries = new ArrayList<TocEntryEntity>();

    public TxtImportConsumer(
            String bookId,
            ContentDatabase db,
            TextBlockBuilder textBlockBuilder,
            TxtImportListener listener
    ) {
        this.bookId = bookId;
        this.db = db;
        this.textBlockBuilder = textBlockBuilder;
        this.listener = listener;
    }

    @Override
    public void onChapter(RawTxtChapter chapter, int chapterIndex) {
        if (chapter == null) return;

        String chapterId = String.valueOf(chapterIndex + 1);
        String prevId = chapterIndex > 0 ? String.valueOf(chapterIndex) : null;
        String nextId = String.valueOf(chapterIndex + 2);

        TitleInfo info = TitleNormalizer.normalizeTitle(chapter.getTitle());

        // 1. Prepare TOC entry
        TocEntryEntity entry = new TocEntryEntity();
        entry.bookId = bookId;
        entry.chapterId = chapterId;
        entry.title = chapter.getTitle();
        entry.orderKey = (long) chapterIndex;
        entry.isExtra = info.isExtra();
        entry.sourcePageIndex = (chapterIndex / 100) + 1;
        tocEntries.add(entry);

        // 2. Prepare chapter blocks
        List<TextBlock> blocks = textBlockBuilder.buildBlocks(chapter.getParagraphs());
        List<ChapterBlockEntity> blockEntities = new ArrayList<ChapterBlockEntity>();
        long chapBytes = 0L;

        for (int i = 0; i < blocks.size(); i++) {
            TextBlock b = blocks.get(i);
            ChapterBlockEntity be = new ChapterBlockEntity();
            be.bookId = bookId;
            be.chapterId = chapterId;
            be.blockIndex = b.getBlockIndex();
            be.startParagraphIndex = b.getStartParagraphIndex();
            be.endParagraphIndex = b.getEndParagraphIndex();
            be.content = b.getText();
            byte[] textBytes = b.getText().getBytes(StandardCharsets.UTF_8);
            chapBytes += textBytes.length;
            blockEntities.add(be);
        }

        // 3. Prepare chapter entity
        ChapterEntity ce = new ChapterEntity();
        ce.bookId = bookId;
        ce.chapterId = chapterId;
        ce.title = chapter.getTitle();
        ce.source = "LOCAL";
        ce.sourceSemanticsVersion = 5;
        ce.isComplete = true;
        ce.missingPagesJson = "[]";
        ce.charCount = chapter.getCharCount();
        ce.bytes = chapBytes;
        ce.prevChapterId = prevId;
        ce.nextChapterId = nextId;
        ce.revision = System.currentTimeMillis();
        ce.fetchedAt = System.currentTimeMillis();

        // 4. Batch insert
        db.chapterDao().insertChapter(ce);
        if (!blockEntities.isEmpty()) {
            db.chapterBlockDao().insertBlocks(blockEntities);
        }
        db.tocDao().insertEntry(entry);

        count++;
        totalBytes += chapBytes;

        if (listener != null) {
            listener.onProgress(count, totalBytes);
        }
    }

    public int getCount() {
        return count;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public List<TocEntryEntity> getTocEntries() {
        return tocEntries;
    }
}
