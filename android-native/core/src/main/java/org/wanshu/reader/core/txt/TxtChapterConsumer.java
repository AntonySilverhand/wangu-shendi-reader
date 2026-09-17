package org.wanshu.reader.core.txt;

public interface TxtChapterConsumer {
    void onChapter(RawTxtChapter chapter, int chapterIndex);
}
