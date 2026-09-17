package org.wanshu.reader.core.net;

import java.net.URI;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UrlSafetyValidator {

    public static final String BOOK_ID = "36780";
    public static final int MAX_REDIRECTS = 5;
    public static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024; // 4 MiB
    public static final int CONNECT_TIMEOUT_MS = 10000; // 10s
    public static final int READ_TIMEOUT_MS = 10000; // 10s

    private static final Pattern CHAPTER_PAGE0_PATTERN = Pattern.compile("^/book/36780_(\\d{1,12})\\.html$");
    private static final Pattern CHAPTER_SUBPAGE_PATTERN = Pattern.compile("^/book/36780/(\\d{1,12})_([1-9]|[1-3]\\d)\\.html$");
    private static final Pattern TOC_PAGE1_PATTERN = Pattern.compile("^/book/36780/?$");
    private static final Pattern TOC_SUBPAGE_PATTERN = Pattern.compile("^/book/36780/([1-9]|[1-7]\\d|80)\\.html$");

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

    public static boolean isAllowedPath(String path) {
        if (path == null || path.isEmpty()) return false;
        if (path.contains("..") || path.contains("//")) return false;

        String lower = path.toLowerCase();
        if (lower.contains("%2e") || lower.contains("%2f") || lower.contains("%5c")) {
            return false;
        }

        if (TOC_PAGE1_PATTERN.matcher(path).matches()) return true;
        if (TOC_SUBPAGE_PATTERN.matcher(path).matches()) return true;
        if (CHAPTER_PAGE0_PATTERN.matcher(path).matches()) return true;
        if (CHAPTER_SUBPAGE_PATTERN.matcher(path).matches()) return true;

        return false;
    }

    public static void validateUrl(String urlStr) throws SecurityException {
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

        if (!isAllowedPath(url.getPath())) {
            throw new SecurityException("Forbidden or unsafe path: " + url.getPath());
        }
    }
}
