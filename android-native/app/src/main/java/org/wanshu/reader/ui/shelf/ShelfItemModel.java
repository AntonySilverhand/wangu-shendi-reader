package org.wanshu.reader.ui.shelf;

import org.wanshu.reader.core.model.ReadingAnchor;
import org.wanshu.reader.data.content.entity.BookEntity;

public class ShelfItemModel {
    private final BookEntity book;
    private ReadingAnchor anchor;
    private String lastChapterTitle;

    public ShelfItemModel(BookEntity book, ReadingAnchor anchor) {
        this.book = book;
        this.anchor = anchor;
        this.lastChapterTitle = "";
    }

    public BookEntity getBook() {
        return book;
    }

    public ReadingAnchor getAnchor() {
        return anchor;
    }

    public void setAnchor(ReadingAnchor anchor) {
        this.anchor = anchor;
    }

    public String getLastChapterTitle() {
        return lastChapterTitle;
    }

    public void setLastChapterTitle(String lastChapterTitle) {
        this.lastChapterTitle = lastChapterTitle;
    }
}
