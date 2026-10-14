package org.wanshu.reader.ui.toc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.wanshu.reader.core.source.TitleNormalizer;
import org.wanshu.reader.data.content.entity.TocEntryEntity;

/** Filters the saved TOC by title, chapter id or chapter number off the UI thread, then reports back on it. */
public final class TocFilterTask implements Runnable {
    public static final int LIMIT = 200;
    private final TocDialog dialog;
    private final List<TocEntryEntity> entries;
    private final String query;
    private final long generation;
    private List<Integer> matches;

    public TocFilterTask(TocDialog dialog, List<TocEntryEntity> entries, String query, long generation) {
        this.dialog = dialog; this.entries = entries; this.query = query; this.generation = generation;
    }

    @Override public void run() {
        if (matches != null) { dialog.applySearchResults(matches, generation); return; }
        String lower = query.toLowerCase(Locale.ROOT);
        Integer number = null;
        try { number = Integer.valueOf(query); } catch (NumberFormatException notNumeric) { number = null; }
        List<Integer> found = new ArrayList<Integer>();
        for (int i = 0; i < entries.size() && found.size() < LIMIT; i++) {
            TocEntryEntity e = entries.get(i);
            if (e.title.toLowerCase(Locale.ROOT).contains(lower) || e.chapterId.equals(query)
                    || (number != null && number.equals(TitleNormalizer.normalizeTitle(e.title).getNumber()))) found.add(i);
        }
        matches = found;
        dialog.postToUi(this);
    }
}
