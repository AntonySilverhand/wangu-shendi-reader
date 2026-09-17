package org.wanshu.reader.core.source;

import java.text.Collator;
import java.util.Comparator;
import org.wanshu.reader.core.model.TocEntry;

public class ExtraTocComparator implements Comparator<TocEntry> {
    private final Collator collator;

    public ExtraTocComparator(Collator collator) {
        this.collator = collator;
    }

    @Override
    public int compare(TocEntry a, TocEntry b) {
        TitleInfo aInfo = TitleNormalizer.normalizeTitle(a.getTitle());
        TitleInfo bInfo = TitleNormalizer.normalizeTitle(b.getTitle());
        int an = aInfo.getNumber() != null ? aInfo.getNumber() : Integer.MAX_VALUE;
        int bn = bInfo.getNumber() != null ? bInfo.getNumber() : Integer.MAX_VALUE;
        if (an != bn) {
            return Integer.compare(an, bn);
        }
        return collator.compare(a.getTitle(), b.getTitle());
    }
}
