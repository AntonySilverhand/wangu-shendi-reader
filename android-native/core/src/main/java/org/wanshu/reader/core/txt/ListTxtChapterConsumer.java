package org.wanshu.reader.core.txt;

import java.util.ArrayList;
import java.util.List;

public class ListTxtChapterConsumer implements TxtChapterConsumer {

    private final List<RawTxtChapter> chapters = new ArrayList<RawTxtChapter>();

    @Override
    public void onChapter(RawTxtChapter chapter, int chapterIndex) {
        if (chapter != null) {
            chapters.add(chapter);
        }
    }

    public List<RawTxtChapter> getChapters() {
        return chapters;
    }
}
