package org.wanshu.reader.core.source;

import java.math.BigInteger;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.wanshu.reader.core.model.TocEntry;

public class TocMerger {

    public static String restTitle(TocEntry entry) {
        String title = entry.getTitle().trim();
        if (title.matches("^分节阅读第\\d+节$")) {
            return "";
        }
        TitleInfo info = TitleNormalizer.normalizeTitle(title);
        String display = info.getDisplayTitle();
        String stripped = display.replaceFirst("^第[\\d零〇一二三四五六七八九十百千万两]+章[\\s　]*", "");
        return stripped;
    }

    public static List<TocEntry> mergeTocPages(List<List<TocEntry>> pages) {
        Map<Integer, List<TocEntry>> groups = new HashMap<Integer, List<TocEntry>>();
        List<Integer> groupOrder = new ArrayList<Integer>();
        List<TocEntry> noNumber = new ArrayList<TocEntry>();
        Set<String> noNumberSeen = new HashSet<String>();
        Set<String> seenIds = new HashSet<String>();

        for (int p = 0; p < pages.size(); p++) {
            List<TocEntry> page = pages.get(p);
            for (int i = 0; i < page.size(); i++) {
                TocEntry entry = page.get(i);
                if (entry.isExtra()) continue;
                if (seenIds.contains(entry.getChapterId())) continue;
                seenIds.add(entry.getChapterId());

                TitleInfo info = TitleNormalizer.normalizeTitle(entry.getTitle());
                Integer num = info.getNumber();

                if (num == null || num <= 0) {
                    if (!noNumberSeen.contains(info.getDisplayTitle())) {
                        noNumberSeen.add(info.getDisplayTitle());
                        noNumber.add(entry);
                    }
                    continue;
                }

                if (!groups.containsKey(num)) {
                    groups.put(num, new ArrayList<TocEntry>());
                    groupOrder.add(num);
                }
                groups.get(num).add(entry);
            }
        }

        List<TocEntry> main = new ArrayList<TocEntry>();
        Collections.sort(groupOrder);

        for (int i = 0; i < groupOrder.size(); i++) {
            int num = groupOrder.get(i);
            List<TocEntry> groupEntries = groups.get(num);
            List<TocEntry> titled = new ArrayList<TocEntry>();
            for (int j = 0; j < groupEntries.size(); j++) {
                if (!restTitle(groupEntries.get(j)).isEmpty()) {
                    titled.add(groupEntries.get(j));
                }
            }
            List<TocEntry> pool = !titled.isEmpty() ? titled : groupEntries;
            Set<String> seenRest = new HashSet<String>();
            for (int j = 0; j < pool.size(); j++) {
                TocEntry entry = pool.get(j);
                String rest = restTitle(entry);
                if (seenRest.contains(rest)) continue;
                seenRest.add(rest);
                main.add(entry);
            }
        }

        main.addAll(noNumber);

        // Extras
        Map<String, TocEntry> extrasByTitle = new HashMap<String, TocEntry>();
        for (int p = 0; p < pages.size(); p++) {
            List<TocEntry> page = pages.get(p);
            for (int i = 0; i < page.size(); i++) {
                TocEntry entry = page.get(i);
                if (!entry.isExtra()) continue;
                TitleInfo info = TitleNormalizer.normalizeTitle(entry.getTitle());
                String dTitle = info.getDisplayTitle();
                TocEntry prev = extrasByTitle.get(dTitle);
                if (prev == null) {
                    extrasByTitle.put(dTitle, entry);
                } else {
                    BigInteger currId = new BigInteger(entry.getChapterId());
                    BigInteger prevId = new BigInteger(prev.getChapterId());
                    if (currId.compareTo(prevId) > 0) {
                        extrasByTitle.put(dTitle, entry);
                    }
                }
            }
        }

        List<TocEntry> extras = new ArrayList<TocEntry>(extrasByTitle.values());
        final Collator collator = Collator.getInstance(Locale.CHINESE);
        Collections.sort(extras, new ExtraTocComparator(collator));

        List<TocEntry> result = new ArrayList<TocEntry>(main.size() + extras.size());
        long orderKey = 0L;
        for (int i = 0; i < main.size(); i++) {
            TocEntry e = main.get(i);
            result.add(new TocEntry(e.getBookId(), e.getChapterId(), e.getTitle(), orderKey++, e.isExtra(), e.getSourcePageIndex()));
        }
        for (int i = 0; i < extras.size(); i++) {
            TocEntry e = extras.get(i);
            result.add(new TocEntry(e.getBookId(), e.getChapterId(), e.getTitle(), orderKey++, e.isExtra(), e.getSourcePageIndex()));
        }

        return result;
    }
}
