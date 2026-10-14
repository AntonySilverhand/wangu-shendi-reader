package org.wanshu.reader.core.net;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.CancellationException;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import javax.net.ssl.SSLException;

public class SourceHttpClient {

    /** Desktop browser identity: the site serves the same page templates the parsers are written for. */
    public static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";
    public static final int MAX_IMAGE_BYTES = 1024 * 1024;
    private static final int MAX_FORM_CHARS = 2048;

    private final HttpConnectionFactory connectionFactory;
    private final boolean allowHttpFallback;

    public SourceHttpClient(HttpConnectionFactory connectionFactory, boolean allowHttpFallback) {
        this.connectionFactory = connectionFactory != null ? connectionFactory : new DefaultHttpConnectionFactory();
        this.allowHttpFallback = allowHttpFallback;
    }

    public SourceHttpClient(boolean allowHttpFallback) {
        this(new DefaultHttpConnectionFactory(), allowHttpFallback);
    }

    public SourceHttpClient() {
        this(new DefaultHttpConnectionFactory(), true);
    }

    /** GET a book, TOC or chapter page as text. */
    public HttpFetchResponse fetch(String urlStr, CancellationToken token) throws IOException {
        return executeWithFallback(UrlSafetyValidator.METHOD_GET, urlStr, null, false,
                UrlSafetyValidator.MAX_RESPONSE_BYTES, token);
    }

    /**
     * POST a url-encoded form (the validator only allows the search endpoint). Redirects are never
     * followed for POST; any 3xx is an error.
     */
    public HttpFetchResponse postForm(String urlStr, String formBody, CancellationToken token) throws IOException {
        if (formBody == null || formBody.length() > MAX_FORM_CHARS) {
            throw new IllegalArgumentException("form body missing or too large");
        }
        byte[] bytes = formBody.getBytes(StandardCharsets.UTF_8);
        return executeWithFallback(UrlSafetyValidator.METHOD_POST, urlStr, bytes, false,
                UrlSafetyValidator.MAX_RESPONSE_BYTES, token);
    }

    /**
     * GET a cover image. A successful response must have an image content type and at most maxBytes;
     * the raw bytes are available through getBodyBytes(). Non-2xx statuses are returned as they are.
     */
    public HttpFetchResponse fetchBytes(String urlStr, CancellationToken token, int maxBytes) throws IOException {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
        return executeWithFallback(UrlSafetyValidator.METHOD_GET, urlStr, null, true, maxBytes, token);
    }

    private HttpFetchResponse executeWithFallback(
            String method,
            String urlStr,
            byte[] formBody,
            boolean image,
            int maxBytes,
            CancellationToken token
    ) throws IOException {
        try {
            return execute(method, urlStr, formBody, image, maxBytes, token);
        } catch (SSLException e) {
            // Plan rule: Do NOT fallback to HTTP on TLS/certificate errors!
            throw e;
        } catch (IOException e) {
            if (allowHttpFallback && urlStr != null && urlStr.startsWith("https://")) {
                String httpUrl = "http://" + urlStr.substring("https://".length());
                // Only fallback if httpUrl is still safe and valid; the retry carries the same method and body.
                try {
                    validate(method, httpUrl, image);
                    return execute(method, httpUrl, formBody, image, maxBytes, token);
                } catch (Exception ignored) {
                    // Fallback failed or forbidden, throw original exception
                    throw e;
                }
            }
            throw e;
        }
    }

    private static void validate(String method, String url, boolean image) {
        if (image) {
            UrlSafetyValidator.validateImageUrl(url);
        } else {
            UrlSafetyValidator.validateUrl(url, method);
        }
    }

    private static boolean isRedirect(int code) {
        return code == 301 || code == 302 || code == 303 || code == 307 || code == 308;
    }

    private HttpFetchResponse execute(
            String method,
            String initialUrl,
            byte[] formBody,
            boolean image,
            int maxBytes,
            CancellationToken token
    ) throws IOException {
        String currentUrl = initialUrl;
        int redirectCount = 0;
        boolean post = UrlSafetyValidator.METHOD_POST.equals(method);

        while (true) {
            if (token != null && token.isCancelled()) {
                throw new CancellationException("Request cancelled before connecting: " + currentUrl);
            }

            validate(method, currentUrl, image);

            URL url = new URL(currentUrl);
            HttpURLConnection conn = connectionFactory.openConnection(url);
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(UrlSafetyValidator.CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(UrlSafetyValidator.READ_TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
            if (image) {
                conn.setRequestProperty("Accept", "image/*");
            } else {
                conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
                conn.setRequestProperty("Accept-Encoding", "gzip, deflate");
            }

            try {
                if (post) {
                    conn.setRequestMethod(UrlSafetyValidator.METHOD_POST);
                    conn.setDoOutput(true);
                    conn.setFixedLengthStreamingMode(formBody.length);
                    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                    conn.connect();
                    OutputStream out = conn.getOutputStream();
                    try {
                        out.write(formBody);
                        out.flush();
                    } finally {
                        out.close();
                    }
                } else {
                    conn.connect();
                }

                int code = conn.getResponseCode();

                // Check redirects
                if (isRedirect(code)) {
                    if (post) {
                        throw new IOException("Unexpected redirect (HTTP " + code + ") on " + currentUrl);
                    }
                    redirectCount++;
                    if (redirectCount > UrlSafetyValidator.MAX_REDIRECTS) {
                        throw new IOException("Too many redirects: " + redirectCount);
                    }

                    String location = conn.getHeaderField("Location");
                    if (location == null || location.isEmpty()) {
                        throw new IOException("Redirect without Location header on " + currentUrl);
                    }

                    URI baseUri = new URI(currentUrl);
                    URI nextUri = baseUri.resolve(location);
                    currentUrl = nextUri.toString();
                    conn.disconnect();
                    continue;
                }

                Long retryAfter = parseRetryAfter(conn.getHeaderField("Retry-After"));
                String contentType = conn.getHeaderField("Content-Type");
                boolean success = code >= 200 && code < 300;

                if (image && success) {
                    if (contentType == null || !contentType.trim().toLowerCase(Locale.US).startsWith("image/")) {
                        throw new IOException("Unexpected content type for image: " + contentType);
                    }
                    String declared = conn.getHeaderField("Content-Length");
                    if (declared != null) {
                        long declaredLength = -1L;
                        try {
                            declaredLength = Long.parseLong(declared.trim());
                        } catch (NumberFormatException ignored) {}
                        if (declaredLength > maxBytes) {
                            throw new IOException("Response exceeded maximum limit of " + maxBytes + " bytes");
                        }
                    }
                }

                InputStream in = null;
                try {
                    if (code >= 200 && code < 400) {
                        in = conn.getInputStream();
                    } else {
                        in = conn.getErrorStream();
                    }

                    if (in == null) {
                        return new HttpFetchResponse(code, "", retryAfter, contentType, currentUrl);
                    }

                    String encoding = conn.getHeaderField("Content-Encoding");
                    if ("gzip".equalsIgnoreCase(encoding)) {
                        in = new GZIPInputStream(in);
                    } else if ("deflate".equalsIgnoreCase(encoding)) {
                        in = new InflaterInputStream(in);
                    }

                    byte[] bodyBytes = readStreamWithLimit(in, maxBytes, token, conn);
                    if (image) {
                        return new HttpFetchResponse(code, "", success ? bodyBytes : null, retryAfter, contentType, currentUrl);
                    }
                    Charset charset = extractCharset(contentType);
                    String body = new String(bodyBytes, charset);

                    return new HttpFetchResponse(code, body, retryAfter, contentType, currentUrl);
                } finally {
                    if (in != null) {
                        try {
                            in.close();
                        } catch (IOException ignored) {}
                    }
                }
            } catch (Exception e) {
                conn.disconnect();
                if (e instanceof IOException) {
                    throw (IOException) e;
                }
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                }
                throw new IOException("HTTP fetch failed on " + currentUrl, e);
            } finally {
                conn.disconnect();
            }
        }
    }

    private static byte[] readStreamWithLimit(
            InputStream in,
            int maxBytes,
            CancellationToken token,
            HttpURLConnection conn
    ) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int total = 0;
        int r;

        while ((r = in.read(buf)) != -1) {
            if (token != null && token.isCancelled()) {
                conn.disconnect();
                throw new CancellationException("Request cancelled during read");
            }
            total += r;
            if (total > maxBytes) {
                conn.disconnect();
                throw new IOException("Response exceeded maximum limit of " + maxBytes + " bytes");
            }
            out.write(buf, 0, r);
        }

        return out.toByteArray();
    }

    public static Long parseRetryAfter(String headerValue) {
        if (headerValue == null || headerValue.trim().isEmpty()) {
            return null;
        }
        String val = headerValue.trim();
        try {
            long seconds = Long.parseLong(val);
            return Math.max(0L, seconds);
        } catch (NumberFormatException ignored) {}

        // Try HTTP-date format (RFC 1123)
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
            Date d = sdf.parse(val);
            if (d != null) {
                long diffMs = d.getTime() - System.currentTimeMillis();
                long diffSec = diffMs / 1000L;
                return Math.max(0L, diffSec);
            }
        } catch (Exception ignored) {}

        return null;
    }

    private static Charset extractCharset(String contentType) {
        if (contentType != null) {
            String lower = contentType.toLowerCase();
            int idx = lower.indexOf("charset=");
            if (idx != -1) {
                String cs = lower.substring(idx + 8).trim();
                int sc = cs.indexOf(';');
                if (sc != -1) cs = cs.substring(0, sc).trim();
                try {
                    return Charset.forName(cs);
                } catch (Exception ignored) {}
            }
        }
        return StandardCharsets.UTF_8;
    }
}
