package org.wanshu.reader.core.source;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SourceUrls {
    /** The book that is on the shelf by default (the only one before multi-book support). */
    public static final String DEFAULT_BOOK_ID = "36780";
    public static final String BOOK_ORIGIN = "https://wanshuge.org";
    public static final String SEARCH_URL = BOOK_ORIGIN + "/search.html";
    public static final int MAX_CHAPTER_PAGES = 40;

    private static final Set<String> ALLOWED_HOSTS = new HashSet<String>(
            Arrays.asList("wanshuge.org", "www.wanshuge.org")
    );

    private static final Pattern BOOK_ID_PATTERN = Pattern.compile("^[1-9]\\d{0,8}$");
    private static final Pattern CHAPTER_ID_PATTERN = Pattern.compile("^\\d{1,12}$");

    private static final Map<String, Pattern[]> LINK_PATTERNS = new HashMap<String, Pattern[]>();

    public static boolean isValidBookId(String id) {
        return id != null && BOOK_ID_PATTERN.matcher(id).matches();
    }

    public static void assertBookId(String id) {
        if (!isValidBookId(id)) {
            throw new IllegalArgumentException("invalid book id: " + id);
        }
    }

    public static void assertChapterId(String id) {
        if (id == null || !CHAPTER_ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("invalid chapter id: " + id);
        }
    }

    /** Cover image URL; the source serves a default picture for books without a cover. */
    public static String coverUrl(String bookId) {
        assertBookId(bookId);
        return BOOK_ORIGIN + "/img/" + bookId + ".jpg";
    }

    public static String tocPath(String bookId, int page) {
        assertBookId(bookId);
        if (page < 1) {
            throw new IllegalArgumentException("bad toc page: " + page);
        }
        if (page == 1) {
            return "/book/" + bookId + "/";
        }
        return "/book/" + bookId + "/" + (page - 1) + ".html";
    }

    public static String chapterPath(String bookId, String id, int pageIndex) {
        assertBookId(bookId);
        assertChapterId(id);
        if (pageIndex < 0 || pageIndex >= MAX_CHAPTER_PAGES) {
            throw new IllegalArgumentException("bad chapter page index: " + pageIndex);
        }
        if (pageIndex == 0) {
            return "/book/" + bookId + "_" + id + ".html";
        }
        return "/book/" + bookId + "/" + id + "_" + pageIndex + ".html";
    }

    public static String buildChapterUrl(String bookId, String id, int pageIndex) {
        return BOOK_ORIGIN + chapterPath(bookId, id, pageIndex);
    }

    public static String buildTocUrl(String bookId, int page) {
        return BOOK_ORIGIN + tocPath(bookId, page);
    }

    private static synchronized Pattern[] linkPatterns(String bookId) {
        Pattern[] cached = LINK_PATTERNS.get(bookId);
        if (cached == null) {
            cached = new Pattern[] {
                    Pattern.compile("^/book/" + bookId + "_(\\d+)\\.html$"),
                    Pattern.compile("^/book/" + bookId + "/(\\d+)(?:_(\\d+))?\\.html$")
            };
            LINK_PATTERNS.put(bookId, cached);
        }
        return cached;
    }

    /** Parses a link to a chapter of the given book; links to other books or hosts yield null. */
    public static ChapterLinkResult parseChapterLink(String bookId, String href) {
        if (href == null || !isValidBookId(bookId)) {
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

        Pattern[] patterns = linkPatterns(bookId);
        Matcher m0 = patterns[0].matcher(h);
        if (m0.matches()) {
            return new ChapterLinkResult(m0.group(1), 0);
        }

        Matcher mn = patterns[1].matcher(h);
        if (mn.matches()) {
            String digits = mn.group(1);
            String pageStr = mn.group(2);
            if (pageStr == null && digits.length() < 6) {
                // TOC pagination link like /book/{id}/1.html
                return null;
            }
            int pIndex;
            try {
                pIndex = pageStr != null ? Integer.parseInt(pageStr) : 0;
            } catch (NumberFormatException e) {
                return null;
            }
            return new ChapterLinkResult(digits, pIndex);
        }

        return null;
    }
}
