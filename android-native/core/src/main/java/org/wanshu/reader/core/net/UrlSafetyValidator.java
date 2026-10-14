package org.wanshu.reader.core.net;

import java.net.URI;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UrlSafetyValidator {

    public static final String METHOD_GET = "GET";
    public static final String METHOD_POST = "POST";
    public static final int MAX_REDIRECTS = 5;
    public static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024; // 4 MiB
    public static final int CONNECT_TIMEOUT_MS = 10000; // 10s
    public static final int READ_TIMEOUT_MS = 10000; // 10s

    // Book ids are 1-9 digits without a leading zero. Chapter sub pages are 1-39, TOC sub pages 1-999.
    private static final Pattern CHAPTER_PAGE0_PATTERN = Pattern.compile("^/book/[1-9]\\d{0,8}_\\d{1,12}\\.html$");
    private static final Pattern CHAPTER_SUBPAGE_PATTERN = Pattern.compile("^/book/[1-9]\\d{0,8}/\\d{1,12}_([1-9]|[1-3]\\d)\\.html$");
    private static final Pattern TOC_PAGE1_PATTERN = Pattern.compile("^/book/[1-9]\\d{0,8}/?$");
    private static final Pattern TOC_SUBPAGE_PATTERN = Pattern.compile("^/book/[1-9]\\d{0,8}/[1-9]\\d{0,2}\\.html$");
    private static final Pattern SEARCH_PATTERN = Pattern.compile("^/search\\.html$");
    private static final Pattern COVER_PATTERN = Pattern.compile("^/img/[1-9]\\d{0,8}\\.jpg$");

    public static boolean isAllowedHost(String host) {
        if (host == null) return false;
        String h = host.toLowerCase().trim();
        return h.equals("wanshuge.org") || h.equals("www.wanshuge.org");
    }

    public static boolean isAllowedProtocol(String protocol) {
        if (protocol == null) return false;
        String p = protocol.toLowerCase().trim();
        return p.equals("https") || p.equals("http");
    }

    public static boolean isAllowedPort(String protocol, int port) {
        if (port == -1) return true;
        if ("https".equalsIgnoreCase(protocol) && port == 443) return true;
        if ("http".equalsIgnoreCase(protocol) && port == 80) return true;
        return false;
    }

    private static boolean hasUnsafeSegments(String path) {
        if (path == null || path.isEmpty()) return true;
        if (path.contains("..") || path.contains("//")) return true;
        String lower = path.toLowerCase();
        return lower.contains("%2e") || lower.contains("%2f") || lower.contains("%5c");
    }

    /** Book, TOC and chapter pages (fetched with GET). */
    public static boolean isAllowedPath(String path) {
        if (hasUnsafeSegments(path)) return false;
        if (TOC_PAGE1_PATTERN.matcher(path).matches()) return true;
        if (TOC_SUBPAGE_PATTERN.matcher(path).matches()) return true;
        if (CHAPTER_PAGE0_PATTERN.matcher(path).matches()) return true;
        if (CHAPTER_SUBPAGE_PATTERN.matcher(path).matches()) return true;
        return false;
    }

    public static boolean isAllowedSearchPath(String path) {
        return !hasUnsafeSegments(path) && SEARCH_PATTERN.matcher(path).matches();
    }

    public static boolean isAllowedCoverPath(String path) {
        return !hasUnsafeSegments(path) && COVER_PATTERN.matcher(path).matches();
    }

    /**
     * Method-aware check: POST is only allowed on the search endpoint; GET on book pages and cover images
     * (the search endpoint is POST only).
     */
    public static boolean isAllowedRequest(String method, String path) {
        if (METHOD_POST.equals(method)) {
            return isAllowedSearchPath(path);
        }
        if (METHOD_GET.equals(method)) {
            return isAllowedPath(path) || isAllowedCoverPath(path);
        }
        return false;
    }

    /** GET of a book, TOC or chapter page. */
    public static void validateUrl(String urlStr) throws SecurityException {
        validateUrl(urlStr, METHOD_GET);
    }

    /** Method-aware validation: POST only for the search endpoint, GET only for book pages. */
    public static void validateUrl(String urlStr, String method) throws SecurityException {
        String path = validateOrigin(urlStr);
        boolean ok = METHOD_POST.equals(method) ? isAllowedSearchPath(path)
                : METHOD_GET.equals(method) && isAllowedPath(path);
        if (!ok) {
            throw new SecurityException("Forbidden or unsafe path for " + method + ": " + path);
        }
    }

    /** GET of a cover image. */
    public static void validateImageUrl(String urlStr) throws SecurityException {
        String path = validateOrigin(urlStr);
        if (!isAllowedCoverPath(path)) {
            throw new SecurityException("Forbidden or unsafe image path: " + path);
        }
    }

    private static String validateOrigin(String urlStr) throws SecurityException {
        if (urlStr == null || urlStr.trim().isEmpty()) {
            throw new SecurityException("URL is null or empty");
        }

        URL url;
        try {
            URI uri = new URI(urlStr);
            if (uri.getUserInfo() != null && !uri.getUserInfo().isEmpty()) {
                throw new SecurityException("Userinfo in URL is forbidden: " + urlStr);
            }
            url = uri.toURL();
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new SecurityException("Malformed URL: " + urlStr, e);
        }

        if (!isAllowedProtocol(url.getProtocol())) {
            throw new SecurityException("Forbidden protocol: " + url.getProtocol());
        }

        if (!isAllowedHost(url.getHost())) {
            throw new SecurityException("Forbidden host: " + url.getHost());
        }

        if (!isAllowedPort(url.getProtocol(), url.getPort())) {
            throw new SecurityException("Forbidden port: " + url.getPort());
        }

        return url.getPath();
    }
}
