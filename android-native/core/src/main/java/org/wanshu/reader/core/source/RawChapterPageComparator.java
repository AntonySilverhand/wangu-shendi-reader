package org.wanshu.reader.core.source;

import java.util.Comparator;

public class RawChapterPageComparator implements Comparator<RawChapterPage> {
    @Override
    public int compare(RawChapterPage a, RawChapterPage b) {
        return Integer.compare(a.getPageIndex(), b.getPageIndex());
    }
}
