package org.wanshu.reader.core.source;

import java.util.Comparator;
import org.wanshu.reader.core.model.TocEntry;

/** Upload order: numeric chapter ids ascending (ids are digit strings up to 12 chars). */
public class TocIdComparator implements Comparator<TocEntry> {
    @Override
    public int compare(TocEntry a, TocEntry b) {
        String x = a.getChapterId(), y = b.getChapterId();
        return x.length() != y.length() ? Integer.compare(x.length(), y.length()) : x.compareTo(y);
    }
}
