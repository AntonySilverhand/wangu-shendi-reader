package org.wanshu.reader.core.source;

import java.math.BigInteger;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    /** Same-title entries whose ids differ by more than this are late re-uploads (the main batch spans ~14k ids). */
    private static final long REUPLOAD_ID_GAP = 1000000L;

    /**
     * Main chapters follow upload order (ascending id), matching the source's own "next chapter" links.
     * The source renumbers backwards in places (e.g. after 第3409章 comes 第三千三百八十九章), so sorting by
     * chapter number would interleave two stretches of the story. Same number + same title keeps the earliest;
     * a "分节阅读第N节" placeholder yields to a titled version in place; late batch re-uploads are dropped.
     */
    public static List<TocEntry> mergeTocPages(List<List<TocEntry>> pages) {
        Map<String, TocEntry> unique = new HashMap<String, TocEntry>();
        for (int p = 0; p < pages.size(); p++) {
            List<TocEntry> page = pages.get(p);
            for (int i = 0; i < page.size(); i++) {
                TocEntry entry = page.get(i);
                if (!entry.isExtra() && !unique.containsKey(entry.getChapterId())) unique.put(entry.getChapterId(), entry);
            }
        }
        List<TocEntry> ordered = new ArrayList<TocEntry>(unique.values());
        Collections.sort(ordered, new TocIdComparator());

        List<TocEntry> main = new ArrayList<TocEntry>();
        Map<Integer, List<Integer>> byNumber = new HashMap<Integer, List<Integer>>();
        Map<String, Long> firstIdByTitle = new HashMap<String, Long>();
        int maxTitledNumber = 0;
        for (int k = 0; k < ordered.size(); k++) {
            TocEntry entry = ordered.get(k);
            Integer parsed = TitleNormalizer.normalizeTitle(entry.getTitle()).getNumber();
            int number = parsed == null || parsed < 0 ? 0 : parsed;
            String rest = restTitle(entry);
            List<Integer> same = byNumber.get(number);
            if (same == null) same = new ArrayList<Integer>();
            if (rest.isEmpty()) {
                if (!same.isEmpty() || maxTitledNumber > number) continue;
            } else {
                boolean duplicate = false;
                Integer placeholder = null;
                for (int j = 0; j < same.size(); j++) {
                    String other = restTitle(main.get(same.get(j)));
                    if (other.equals(rest)) duplicate = true;
                    if (other.isEmpty() && placeholder == null) placeholder = same.get(j);
                }
                if (duplicate) continue;
                long id = Long.parseLong(entry.getChapterId());
                Long firstId = firstIdByTitle.get(rest);
                if (firstId != null && id - firstId > REUPLOAD_ID_GAP) continue;
                if (firstId == null) firstIdByTitle.put(rest, id);
                maxTitledNumber = Math.max(maxTitledNumber, number);
                if (placeholder != null) {
                    main.set(placeholder, entry);
                    continue;
                }
            }
            same.add(main.size());
            byNumber.put(number, same);
            main.add(entry);
        }

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
