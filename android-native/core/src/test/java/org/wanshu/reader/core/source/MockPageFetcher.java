package org.wanshu.reader.core.source;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.wanshu.reader.core.util.JavaBase64Decoder;

public class MockPageFetcher implements PageFetcher {
    private final Map<Integer, String> pages = new HashMap<Integer, String>();
    private final Set<Integer> failPages = new HashSet<Integer>();
    private final JavaBase64Decoder decoder = new JavaBase64Decoder();

    public void addPage(int pageIndex, String html) {
        pages.put(pageIndex, html);
    }

    public void addFailure(int pageIndex) {
        failPages.add(pageIndex);
    }

    @Override
    public PageFetchOutcome fetch(String chapterId, int pageIndex) {
        if (failPages.contains(pageIndex)) {
            return PageFetchOutcome.failed(new RuntimeException("Simulated network failure on page " + pageIndex));
        }
        String html = pages.get(pageIndex);
        if (html == null) {
            return PageFetchOutcome.notFound();
        }
        RawChapterPage page = SourceParser.parseChapterPage(html, chapterId, pageIndex, decoder);
        return PageFetchOutcome.ok(page);
    }
}
