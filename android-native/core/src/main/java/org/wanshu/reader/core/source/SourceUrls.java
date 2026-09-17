package org.wanshu.reader.core.source;

import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SourceUrls {
    public static final String BOOK_ID = "36780";
    public static final String BOOK_ORIGIN = "https://wanshuge.org";
    public static final int MAX_CHAPTER_PAGES = 40;
    public static final int TOC_PAGE_FALLBACK = 44;

    private static final Set<String> ALLOWED_HOSTS = new HashSet<String>(
            Arrays.asList("wanshuge.org", "www.wanshuge.org")
    );

    private static final Pattern CHAPTER_ID_PATTERN = Pattern.compile("^\\d{1,12}$");
    private static final Pattern CHAPTER_P0_PATTERN = Pattern.compile("^/book/36780_(\\d+)\\.html$");
    private static final Pattern CHAPTER_PN_PATTERN = Pattern.compile("^/book/36780/(\\d+)(?:_(\\d+))?\\.html$");

    public static void assertChapterId(String id) {
        if (id == null || !CHAPTER_ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("invalid chapter id: " + id);
        }
    }

    public static String tocPath(int page) {
        if (page < 1) {
            throw new IllegalArgumentException("bad toc page: " + page);
        }
        if (page == 1) {
            return "/book/" + BOOK_ID + "/";
        }
        return "/book/" + BOOK_ID + "/" + (page - 1) + ".html";
    }

    public static String chapterPath(String id, int pageIndex) {
        assertChapterId(id);
        if (pageIndex < 0 || pageIndex >= MAX_CHAPTER_PAGES) {
            throw new IllegalArgumentException("bad chapter page index: " + pageIndex);
        }
        if (pageIndex == 0) {
            return "/book/" + BOOK_ID + "_" + id + ".html";
        }
        return "/book/" + BOOK_ID + "/" + id + "_" + pageIndex + ".html";
    }

    public static String buildChapterUrl(String id, int pageIndex) {
        return BOOK_ORIGIN + chapterPath(id, pageIndex);
    }

    public static String buildTocUrl(int page) {
        return BOOK_ORIGIN + tocPath(page);
    }

    public static ChapterLinkResult parseChapterLink(String href) {
        if (href == null) {
            return null;
        }
        String h = href.trim();
        if (h.isEmpty()) {
            return null;
        }

        int hashIdx = h.indexOf('#');
        if (hashIdx >= 0) {
            h = h.substring(0, hashIdx);
        }

        if (h.startsWith("//") || h.startsWith("http://") || h.startsWith("https://")) {
            try {
                String toParse = h.startsWith("//") ? "https:" + h : h;
                URI uri = new URI(toParse);
                String host = uri.getHost();
                if (host == null || !ALLOWED_HOSTS.contains(host.toLowerCase())) {
                    return null;
                }
                h = uri.getPath();
                if (h == null) {
                    return null;
                }
            } catch (Exception e) {
                return null;
            }
        }

        int queryIdx = h.indexOf('?');
        if (queryIdx >= 0) {
            h = h.substring(0, queryIdx);
        }

        Matcher m0 = CHAPTER_P0_PATTERN.matcher(h);
        if (m0.matches()) {
            return new ChapterLinkResult(m0.group(1), 0);
        }

        Matcher mn = CHAPTER_PN_PATTERN.matcher(h);
        if (mn.matches()) {
            String digits = mn.group(1);
            String pageStr = mn.group(2);
            if (pageStr == null && digits.length() < 6) {
                // TOC pagination link like /book/36780/1.html
                return null;
            }
            int pIndex = pageStr != null ? Integer.parseInt(pageStr) : 0;
            return new ChapterLinkResult(digits, pIndex);
        }

        return null;
    }
}
