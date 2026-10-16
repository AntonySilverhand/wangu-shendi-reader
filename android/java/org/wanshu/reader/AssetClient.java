package org.wanshu.reader;

import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 自定义 WebViewClient（顶层类，禁止内部类/lambda）：
 *  - 页面 origin 固定为 https://reader.local/（不依赖随机端口 → Web Storage/IndexedDB
 *    跨启动稳定）；
 *  - 所有资源请求由 shouldInterceptRequest 从 APK assets 提供；
 *  - /native/source 走受限书源代理（只允许万书阁/69书吧上校验过的书号路径与站内搜索）；
 *  - /native/cover?book= 取封面图（只允许书号拼出的封面路径）；
 *  - /native/download?book= 取整本 TXT 压缩包（只允许书号拼出的下载地址）；
 *  - 任何 reader.local 之外的请求一律拒绝（不开放任意 URL 代理）。
 */
public final class AssetClient extends WebViewClient {
  private static final String ORIGIN_HOST = "reader.local";
  private static final String NOT_FOUND = "not found";
  private static final String FORBIDDEN = "forbidden";

  private final AssetProvider provider;
  private final SourceProxy proxy;
  public AssetClient(AssetProvider provider, SourceProxy proxy) {
    this.provider = provider;
    this.proxy = proxy;
  }

  @Override
  public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
    String method = "GET";
    try {
      method = request.getMethod();
    } catch (Exception ignored) {
    }
    if (!"GET".equals(method) && !"HEAD".equals(method)) {
      return response("text/plain", 405, "method not allowed");
    }
    return handle(request.getUrl().toString());
  }

  @Override
  public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
    return handle(url);
  }

  @Override
  public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
    // 只允许停留在 reader.local 内；其余导航一律拒绝
    return shouldOverrideUrlLoading(view, request.getUrl().toString());
  }

  @Override
  public boolean shouldOverrideUrlLoading(WebView view, String url) {
    try {
      java.net.URL parsed = new java.net.URL(url);
      return !"https".equals(parsed.getProtocol()) || parsed.getPort() != -1
          || parsed.getUserInfo() != null || !isReaderLocal(parsed.getHost());
    } catch (Exception e) {
      return true;
    }
  }

  private WebResourceResponse handle(String rawUrl) {
    java.net.URL url;
    try {
      url = new java.net.URL(rawUrl);
    } catch (Exception e) {
      return response("text/plain", 400, "bad url");
    }
    if (!"https".equals(url.getProtocol()) || url.getPort() != -1 || url.getUserInfo() != null || !isReaderLocal(url.getHost())) {
      // 外域一律拒绝：不代理、不泄露
      return response("text/plain", 403, FORBIDDEN);
    }
    String path = url.getPath();
    if (path == null || path.isEmpty() || path.equals("/")) path = "/index.html";
    if (path.equals("/native/source")) {
      return handleSource(url.getQuery());
    }
    if (path.equals("/native/cover")) {
      return handleCover(url.getQuery());
    }
    if (path.equals("/native/download")) {
      return handleDownload(url.getQuery());
    }
    try {
      byte[] data = provider.open(path);
      if (data == null) {
        return response("text/plain", 404, NOT_FOUND);
      }
      String mime = AssetProvider.mimeOf(path);
      String encoding = isText(mime) ? "utf-8" : null;
      Map<String, String> headers = new java.util.HashMap<>();
      headers.put("Cache-Control", "no-cache");
      return new WebResourceResponse(mime, encoding, 200, "OK", headers,
          new ByteArrayInputStream(data));
    } catch (Exception e) {
      return response("text/plain", 500, "asset error");
    }
  }

  /** 查询参数取值（URL 解码）；不存在时返回 null */
  private static String queryParam(String query, String name) {
    if (query == null) return null;
    for (String pair : query.split("&")) {
      int eq = pair.indexOf('=');
      if (eq > 0 && pair.substring(0, eq).equals(name)) {
        try {
          return URLDecoder.decode(pair.substring(eq + 1), "UTF-8");
        } catch (Exception e) {
          return null;
        }
      }
    }
    return null;
  }

  private WebResourceResponse handleCover(String query) {
    SourceResult result = proxy.fetchCover(queryParam(query, "book"));
    if (result.status != 200) {
      return response("text/plain", result.status, result.errorMessage);
    }
    Map<String, String> headers = new java.util.HashMap<>();
    String mime = result.contentType.split(";")[0].trim();
    headers.put("Content-Type", mime);
    headers.put("Cache-Control", "max-age=604800");
    return new WebResourceResponse(mime, null, 200, "OK", headers,
        new ByteArrayInputStream(result.body));
  }

  private WebResourceResponse handleDownload(String query) {
    SourceResult result = proxy.fetchDownload(queryParam(query, "book"));
    if (result.status != 200) {
      return response("text/plain", result.status, result.errorMessage);
    }
    Map<String, String> headers = new java.util.HashMap<>();
    headers.put("Content-Type", "application/zip");
    headers.put("Content-Length", String.valueOf(result.body.length));
    headers.put("Cache-Control", "no-store");
    return new WebResourceResponse("application/zip", null, 200, "OK", headers,
        new ByteArrayInputStream(result.body));
  }

  private WebResourceResponse handleSource(String query) {
    String target = queryParam(query, "url");
    if (target == null || target.isEmpty()) {
      return response("text/plain", 400, "missing url");
    }
    // 站内搜索：WebView 拦截时拿不到请求体，表单放在 query 中由原生以 POST 发出
    SourceResult result = proxy.fetch(target, queryParam(query, "form"));
    if (result.status != 200) {
      return response("text/plain", result.status, result.errorMessage);
    }
    // 原始字节 + 上游字符集（GBK 页面不能当 UTF-8 转发），JS 侧按 Content-Type 解码
    String contentType = result.contentType != null ? result.contentType : "text/html; charset=utf-8";
    String charset = SourceRules.charsetOf(contentType, null);
    Map<String, String> headers = new java.util.HashMap<>();
    headers.put("Content-Type", contentType);
    headers.put("Cache-Control", "no-store");
    if (result.finalUrl != null) headers.put("X-Final-Url", result.finalUrl);
    return new WebResourceResponse("text/html", charset, 200, "OK", headers,
        new ByteArrayInputStream(result.body));
  }

  private static WebResourceResponse response(String mime, int status, String text) {
    byte[] body = text.getBytes(StandardCharsets.UTF_8);
    Map<String, String> headers = new java.util.HashMap<>();
    headers.put("Content-Type", mime + "; charset=utf-8");
    headers.put("Cache-Control", "no-store");
    return new WebResourceResponse(mime, "utf-8", status,
        status == 200 ? "OK" : status == 403 ? "Forbidden"
            : status == 404 ? "Not Found" : status == 405 ? "Method Not Allowed" : "Error",
        headers, new ByteArrayInputStream(body));
  }

  private static boolean isReaderLocal(String host) {
    return host != null && host.equalsIgnoreCase(ORIGIN_HOST);
  }

  private static boolean isText(String mime) {
    return mime.startsWith("text/") || mime.contains("javascript")
        || mime.contains("json") || mime.contains("svg") || mime.contains("xml");
  }
}
