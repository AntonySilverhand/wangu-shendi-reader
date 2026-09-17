package org.wanshu.reader.migration;

import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class LegacyMigrationClient extends WebViewClient {
    private static final String ALLOWED_ORIGIN = "https://reader.local";
    private static final String MIGRATION_PATH = "/native-migration.html";
    private final byte[] migrationHtmlBytes;

    public LegacyMigrationClient(byte[] migrationHtmlBytes) {
        this.migrationHtmlBytes = migrationHtmlBytes;
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        if (request == null || request.getUrl() == null) {
            return createResponse(400, "Bad Request", "text/plain");
        }
        return handleRequest(request.getUrl().toString());
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
        return handleRequest(url);
    }

    private WebResourceResponse handleRequest(String url) {
        if (url == null) {
            return createResponse(400, "Bad Request", "text/plain");
        }
        try {
            java.net.URL parsed = new java.net.URL(url);
            if (!"https".equalsIgnoreCase(parsed.getProtocol()) || !"reader.local".equalsIgnoreCase(parsed.getHost())) {
                return createResponse(403, "Forbidden", "text/plain");
            }

            String path = parsed.getPath();
            if (path == null || path.isEmpty() || "/".equals(path) || MIGRATION_PATH.equals(path)) {
                Map<String, String> headers = new HashMap<String, String>();
                headers.put("Content-Type", "text/html; charset=utf-8");
                headers.put("Content-Security-Policy", "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline';");
                headers.put("Cache-Control", "no-store");
                return new WebResourceResponse(
                        "text/html",
                        "utf-8",
                        200,
                        "OK",
                        headers,
                        new ByteArrayInputStream(migrationHtmlBytes)
                );
            }
            return createResponse(404, "Not Found", "text/plain");
        } catch (Exception e) {
            return createResponse(400, "Bad Request", "text/plain");
        }
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        return true;
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        return true;
    }

    private WebResourceResponse createResponse(int code, String message, String mime) {
        byte[] body = message.getBytes(StandardCharsets.UTF_8);
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Content-Type", mime + "; charset=utf-8");
        headers.put("Cache-Control", "no-store");
        return new WebResourceResponse(mime, "utf-8", code, message, headers, new ByteArrayInputStream(body));
    }
}
