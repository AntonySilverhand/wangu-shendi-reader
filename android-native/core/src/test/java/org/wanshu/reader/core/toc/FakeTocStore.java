package org.wanshu.reader.core.toc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.wanshu.reader.core.model.TocEntry;

public class FakeTocStore {
    public final Map<Integer, List<TocEntry>> loaded = new HashMap<Integer, List<TocEntry>>();
    public final Set<Integer> failed = new HashSet<Integer>();
    public final Set<Integer> loading = new HashSet<Integer>();
    public final int totalPages;
    public final int perPage;
    public final List<String> loadLog = new ArrayList<String>();
    public final List<String> searchLog = new ArrayList<String>();

    public FakeTocStore(int totalPages, int perPage) {
        this.totalPages = totalPages;
        this.perPage = perPage;
    }

    public List<TocEntry> allEntries() {
        List<Integer> pages = new ArrayList<Integer>(loaded.keySet());
        Collections.sort(pages);
        List<TocEntry> all = new ArrayList<TocEntry>();
        for (int i = 0; i < pages.size(); i++) {
            List<TocEntry> pEntries = loaded.get(pages.get(i));
            if (pEntries != null) {
                all.addAll(pEntries);
            }
        }
        return all;
    }

    public List<TocEntry> entriesOf(int page) {
        List<TocEntry> list = new ArrayList<TocEntry>();
        for (int i = 0; i < perPage; i++) {
            int n = (page - 1) * perPage + i + 1;
            list.add(new TocEntry(
                    "36780",
                    String.valueOf(38000000 + n),
                    "第" + n + "章 测试",
                    (long) n,
                    false,
                    page
            ));
        }
        return list;
    }
}
