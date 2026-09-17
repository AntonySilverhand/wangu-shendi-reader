package org.wanshu.reader.core.source;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.wanshu.reader.core.model.TocEntry;

public class ClassifiedToc {
    private final List<TocEntry> main;
    private final List<TocEntry> extras;

    public ClassifiedToc(List<TocEntry> main, List<TocEntry> extras) {
        this.main = main != null
                ? Collections.unmodifiableList(new ArrayList<TocEntry>(main))
                : Collections.<TocEntry>emptyList();
        this.extras = extras != null
                ? Collections.unmodifiableList(new ArrayList<TocEntry>(extras))
                : Collections.<TocEntry>emptyList();
    }

    public List<TocEntry> getMain() {
        return main;
    }

    public List<TocEntry> getExtras() {
        return extras;
    }
}
