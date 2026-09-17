package org.wanshu.reader.core.net;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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

    public HttpFetchResponse fetch(String urlStr, CancellationToken token) throws IOException {
        try {
            return executeWithRedirects(urlStr, token);
        } catch (SSLException e) {
            // Plan rule: Do NOT fallback to HTTP on TLS/certificate errors!
            throw e;
        } catch (IOException e) {
            if (allowHttpFallback && urlStr != null && urlStr.startsWith("https://")) {
                String httpUrl = "http://" + urlStr.substring("https://".length());
                // Only fallback if httpUrl is still safe and valid
                try {
                    UrlSafetyValidator.validateUrl(httpUrl);
                    return executeWithRedirects(httpUrl, token);
                } catch (Exception ignored) {
                    // Fallback failed or forbidden, throw original exception
                    throw e;
                }
            }
            throw e;
        }
    }

    private HttpFetchResponse executeWithRedirects(String initialUrl, CancellationToken token) throws IOException {
        String currentUrl = initialUrl;
        int redirectCount = 0;

        while (true) {
            if (token != null && token.isCancelled()) {
                throw new CancellationException("Request cancelled before connecting: " + currentUrl);
            }

            UrlSafetyValidator.validateUrl(currentUrl);

            URL url = new URL(currentUrl);
            HttpURLConnection conn = connectionFactory.openConnection(url);
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(UrlSafetyValidator.CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(UrlSafetyValidator.READ_TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            conn.setRequestProperty("Accept-Encoding", "gzip, deflate");

            try {
                conn.connect();

                int code = conn.getResponseCode();

                // Check redirects
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
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

                    byte[] bodyBytes = readStreamWithLimit(in, UrlSafetyValidator.MAX_RESPONSE_BYTES, token, conn);
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
