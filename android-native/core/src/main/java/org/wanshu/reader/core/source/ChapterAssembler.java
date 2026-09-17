package org.wanshu.reader.core.source;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.wanshu.reader.core.model.ChapterResult;

public class ChapterAssembler {
    private final PageFetcher fetcher;

    public ChapterAssembler(PageFetcher fetcher) {
        this.fetcher = fetcher;
    }

    public ChapterResult assemble(String bookId, String chapterId) {
        SourceUrls.assertChapterId(chapterId);

        Map<Integer, RawChapterPage> fetched = new HashMap<Integer, RawChapterPage>();
        Set<Integer> failed = new HashSet<Integer>();
        Set<Integer> absent = new HashSet<Integer>();
        Set<Integer> seen = new HashSet<Integer>();
        Set<Integer> discovered = new HashSet<Integer>();
        Set<String> signatures = new HashSet<String>();
        List<Integer> queue = new ArrayList<Integer>();
        queue.add(0);

        boolean probePending = false;
        boolean terminal = false;

        while (fetched.size() < SourceUrls.MAX_CHAPTER_PAGES) {
            if (queue.isEmpty()) {
                int maxIdx = -1;
                for (Integer k : fetched.keySet()) {
                    if (k > maxIdx) {
                        maxIdx = k;
                    }
                }
                if (maxIdx < 0) {
                    break;
                }
                RawChapterPage last = fetched.get(maxIdx);

                // Terminal evidence A: last page has nextChapterId and maxIdx > 0
                if (last != null && last.getNextChapterId() != null && maxIdx > 0) {
                    terminal = true;
                    break;
                }

                // Terminal evidence B: absent contains maxIdx + 1 (loopback or 404)
                if (absent.contains(maxIdx + 1)) {
                    terminal = true;
                    break;
                }

                // Terminal evidence C: finite probe
                if (probePending) {
                    terminal = false;
                    break;
                }

                int probeIdx = maxIdx + 1;
                while (probeIdx < SourceUrls.MAX_CHAPTER_PAGES &&
                        (seen.contains(probeIdx) || failed.contains(probeIdx) || absent.contains(probeIdx))) {
                    probeIdx++;
                }
                if (probeIdx >= SourceUrls.MAX_CHAPTER_PAGES) {
                    terminal = false;
                    break;
                }
                probePending = true;
                queue.add(probeIdx);
                continue;
            }

            Collections.sort(queue);
            int pageIndex = queue.remove(0);
            if (seen.contains(pageIndex) || pageIndex < 0 || pageIndex >= SourceUrls.MAX_CHAPTER_PAGES) {
                continue;
            }
            seen.add(pageIndex);

            PageFetchOutcome outcome = fetcher.fetch(chapterId, pageIndex);
            if (outcome.getKind() == PageFetchOutcomeKind.OK) {
                RawChapterPage page = outcome.getPage();
                if (page.getParagraphs().isEmpty()) {
                    failed.add(pageIndex);
                    continue;
                }

                // Check loopback
                String sig = joinParagraphs(page.getParagraphs());
                boolean isLoop = signatures.contains(sig) ||
                        (page.getDeclaredPageIndex() != null && page.getDeclaredPageIndex() < page.getPageIndex());

                if (isLoop) {
                    absent.add(pageIndex);
                    for (int pi : page.getSameChapterPages()) {
                        discovered.add(pi);
                        if (pi >= 0 && pi < SourceUrls.MAX_CHAPTER_PAGES && !seen.contains(pi) && !queue.contains(pi)) {
                            queue.add(pi);
                        }
                    }
                    continue;
                }

                signatures.add(sig);
                fetched.put(page.getPageIndex(), page);
                for (int pi : page.getSameChapterPages()) {
                    discovered.add(pi);
                    if (pi >= 0 && pi < SourceUrls.MAX_CHAPTER_PAGES && !seen.contains(pi) && !queue.contains(pi)) {
                        queue.add(pi);
                    }
                }
            } else if (outcome.getKind() == PageFetchOutcomeKind.NOT_FOUND) {
                absent.add(pageIndex);
            } else {
                failed.add(pageIndex);
            }
        }

        if (fetched.size() >= SourceUrls.MAX_CHAPTER_PAGES) {
            terminal = false;
        }

        Set<Integer> missing = new HashSet<Integer>(failed);
        for (int pi : discovered) {
            if (!fetched.containsKey(pi) && !absent.contains(pi) && pi < SourceUrls.MAX_CHAPTER_PAGES) {
                missing.add(pi);
            }
        }
        List<Integer> sortedMissing = new ArrayList<Integer>(missing);
        Collections.sort(sortedMissing);

        if (fetched.isEmpty()) {
            return new ChapterResult(
                    bookId,
                    chapterId,
                    "章节 " + chapterId,
                    Collections.<String>emptyList(),
                    null,
                    null,
                    0,
                    0,
                    false,
                    sortedMissing
            );
        }

        List<RawChapterPage> sortedPages = new ArrayList<RawChapterPage>(fetched.values());
        Collections.sort(sortedPages, new RawChapterPageComparator());

        List<String> paragraphs = new ArrayList<String>();
        Set<String> pageSignatures = new HashSet<String>();

        for (int p = 0; p < sortedPages.size(); p++) {
            RawChapterPage page = sortedPages.get(p);
            String sig = joinParagraphs(page.getParagraphs());
            if (!page.getParagraphs().isEmpty() && pageSignatures.contains(sig)) {
                continue; // Entire page re-sent
            }
            pageSignatures.add(sig);

            List<String> pageParas = page.getParagraphs();
            boolean isAdjacentToPrev = (p > 0 && sortedPages.get(p - 1).getPageIndex() + 1 == page.getPageIndex());

            for (int i = 0; i < pageParas.size(); i++) {
                String para = pageParas.get(i);
                // Boundary deduplication: ONLY when moving from adjacent previous page,
                // and it is the first paragraph of this page, and matches last paragraph of previous page.
                if (i == 0 && isAdjacentToPrev && !paragraphs.isEmpty() && paragraphs.get(paragraphs.size() - 1).equals(para)) {
                    continue;
                }
                // Intra-page duplicate sentences are preserved!
                paragraphs.add(para);
            }
        }

        RawChapterPage firstPage = sortedPages.get(0);
        RawChapterPage lastPage = sortedPages.get(sortedPages.size() - 1);

        String chosenTitle = firstPage.getTitle();
        for (int i = 0; i < sortedPages.size(); i++) {
            String t = sortedPages.get(i).getTitle();
            if (!t.matches(".*第\\d+页.*")) {
                chosenTitle = t;
                break;
            }
        }

        int totalChars = 0;
        for (int i = 0; i < paragraphs.size(); i++) {
            totalChars += paragraphs.get(i).length();
        }

        boolean complete = terminal && missing.isEmpty();

        String nextId = lastPage.getNextChapterId() != null
                ? lastPage.getNextChapterId()
                : firstPage.getNextChapterId();

        return new ChapterResult(
                bookId,
                chapterId,
                chosenTitle,
                paragraphs,
                firstPage.getPrevChapterId(),
                nextId,
                sortedPages.size(),
                totalChars,
                complete,
                sortedMissing
        );
    }

    private static String joinParagraphs(List<String> paras) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < paras.size(); i++) {
            if (i > 0) {
                sb.append('\u0001');
            }
            sb.append(paras.get(i));
        }
        return sb.toString();
    }
}
