package org.wanshu.reader.data.txt;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.wanshu.reader.core.text.TextBlockBuilder;
import org.wanshu.reader.core.txt.TxtSplitter;
import org.wanshu.reader.data.content.ContentDatabase;
import org.wanshu.reader.data.content.entity.BookEntity;
import org.wanshu.reader.data.content.entity.ChapterEntity;
import org.wanshu.reader.data.content.entity.TocPageEntity;

public class TxtImporter {

    public static String importStream(
            InputStream in,
            String bookTitle,
            ContentDatabase db,
            TextBlockBuilder blockBuilder,
            TxtImportListener listener
    ) {
        final String bookId = "local-" + System.currentTimeMillis();
        final String safeTitle = (bookTitle != null && !bookTitle.trim().isEmpty())
                ? bookTitle.trim()
                : "本地导入";

        // 1. Initial book record: IMPORTING
        BookEntity book = new BookEntity();
        book.bookId = bookId;
        book.sourceType = "LOCAL_TXT";
        book.title = safeTitle;
        book.author = "本地导入";
        book.importStatus = "IMPORTING";
        book.createdAt = System.currentTimeMillis();
        book.updatedAt = System.currentTimeMillis();
        db.bookDao().insertBook(book);

        TxtImportConsumer consumer = new TxtImportConsumer(bookId, db, blockBuilder, listener);

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            TxtSplitter.split(reader, consumer);

            if (consumer.getCount() == 0) {
                db.bookDao().deleteBook(bookId);
                throw new IllegalArgumentException("没有识别到任何章节");
            }

            // Fix last chapter nextChapterId to null
            String lastChapterId = String.valueOf(consumer.getCount());
            ChapterEntity lastChap = db.chapterDao().getChapter(bookId, lastChapterId);
            if (lastChap != null) {
                lastChap.nextChapterId = null;
                db.chapterDao().insertChapter(lastChap);
            }

            // Create TOC pages (100 entries per page)
            int totalChapters = consumer.getCount();
            int totalPages = (totalChapters + 99) / 100;
            java.util.List<TocPageEntity> pages = new java.util.ArrayList<TocPageEntity>();
            for (int p = 1; p <= totalPages; p++) {
                TocPageEntity pe = new TocPageEntity();
                pe.bookId = bookId;
                pe.pageIndex = p;
                pe.status = "LOADED";
                pe.error = "";
                pe.updatedAt = System.currentTimeMillis();
                pages.add(pe);
            }
            db.tocDao().insertTocPages(pages);

            // Update book to READY
            book.importStatus = "READY";
            book.chapterCount = totalChapters;
            book.totalBytes = consumer.getTotalBytes();
            book.updatedAt = System.currentTimeMillis();
            db.bookDao().insertBook(book);

            if (listener != null) {
                listener.onSuccess(bookId, totalChapters, consumer.getTotalBytes());
            }

            return bookId;
        } catch (Throwable t) {
            // Clean up staging on failure
            try {
                db.chapterBlockDao().deleteBlocksForBook(bookId);
                db.chapterDao().deleteChaptersForBook(bookId);
                db.tocDao().deleteEntriesForBook(bookId);
                db.tocDao().deleteTocPagesForBook(bookId);
                book.importStatus = "FAILED";
                db.bookDao().insertBook(book);
            } catch (Exception ignored) {}

            if (listener != null) {
                listener.onError(t);
            }
            throw new RuntimeException("TXT import failed", t);
        }
    }
}
