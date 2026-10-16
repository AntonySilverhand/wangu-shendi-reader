package org.wanshu.reader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** 请求由 JS source.ts 队列调度；原生仅提供受限传输，不自行重试。白名单见 SourceRules。 */
final class SourceProxy {
  private static final int MAX_BODY = 4 * 1024 * 1024;
  private static final int MAX_IMAGE = 1024 * 1024;

  /** 目录/章节页（GET）；form 非空时为站内搜索（POST） */
  SourceResult fetch(String rawUrl, String form) {
    try {
      URL url = new URL(rawUrl);
      if (form != null) return post(url, form);
      // 每一跳都先验证，不跟随后再检查（禁止向任意地址发请求）。
      for (int hop = 0; hop < 5; hop++) {
        if (!SourceRules.allowedPage(url)) return error(403, "forbidden");
        HttpURLConnection conn = open(url, "text/html,application/xhtml+xml");
        try {
          int status = conn.getResponseCode();
          if (status >= 300 && status <= 399) {
            String location = conn.getHeaderField("Location");
            if (location == null) return error(502, "redirect without location");
            url = new URL(url, location);
            continue;
          }
          // 保留 404：章节分页探针依赖它作为终章证据。
          if (status != 200) return upstreamError(conn, status);
          return html(conn, url);
        } finally {
          conn.disconnect();
        }
      }
      return error(502, "too many redirects");
    } catch (Exception e) {
      return error(502, "fetch failed: " + e.getMessage());
    }
  }

  /** 封面图：只允许书号拼出的封面路径（万书阁 /img/{书号}.jpg、69书吧 static 图床），返回图片字节 */
  SourceResult fetchCover(String bookId) {
    String target = SourceRules.coverUrl(bookId);
    if (target == null) return error(400, "bad book");
    try {
      URL url = new URL(target);
      if (!SourceRules.allowedCover(url)) return error(403, "forbidden");
      HttpURLConnection conn = open(url, "image/*");
      try {
        int status = conn.getResponseCode();
        if (status != 200) return error(status >= 400 && status <= 599 ? status : 502, "upstream HTTP " + status);
        String type = conn.getContentType();
        if (type == null || !type.toLowerCase().startsWith("image/")) return error(502, "not an image");
        return new SourceResult(200, read(conn, MAX_IMAGE), url.toString(), null, type);
      } finally {
        conn.disconnect();
      }
    } catch (Exception e) {
      return error(502, "cover failed: " + e.getMessage());
    }
  }

  private SourceResult post(URL url, String form) throws IOException {
    if (!SourceRules.allowedSearch(url, form)) return error(403, "forbidden");
    HttpURLConnection conn = open(url, "text/html,application/xhtml+xml");
    try {
      byte[] body = form.getBytes(StandardCharsets.UTF_8);
      conn.setRequestMethod("POST");
      conn.setDoOutput(true);
      conn.setFixedLengthStreamingMode(body.length);
      conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
      try (OutputStream out = conn.getOutputStream()) {
        out.write(body);
      }
      int status = conn.getResponseCode();
      // 只有一个结果时 jieqi 搜索会重定向到书籍详情页：目标按页面白名单逐跳验证后以 GET 获取
      if (status >= 300 && status <= 399) {
        String location = conn.getHeaderField("Location");
        if (location == null) return error(502, "redirect without location");
        URL target = new URL(url, location);
        if (!SourceRules.allowedPage(target)) return error(403, "forbidden redirect");
        return fetch(target.toString(), null);
      }
      if (status != 200) return upstreamError(conn, status);
      return html(conn, url);
    } finally {
      conn.disconnect();
    }
  }

  /** HTML 原始字节原样返回，并带上字符集（69书吧为 GBK），由 JS 按字符集解码 */
  private static SourceResult html(HttpURLConnection conn, URL url) throws IOException {
    byte[] body = read(conn, MAX_BODY);
    String charset = SourceRules.charsetOf(conn.getContentType(), body);
    return new SourceResult(200, body, url.toString(), null, "text/html; charset=" + charset);
  }

  /** 人机验证（Cloudflare challenge）带 cf-challenge 标记，JS 侧据此报告 blocked；不尝试绕过 */
  private static SourceResult upstreamError(HttpURLConnection conn, int status) {
    if ("challenge".equalsIgnoreCase(conn.getHeaderField("cf-mitigated"))) {
      return error(403, "cf-challenge");
    }
    return error(status >= 400 && status <= 599 ? status : 502, "upstream HTTP " + status);
  }

  private static HttpURLConnection open(URL url, String accept) throws IOException {
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setInstanceFollowRedirects(false);
    conn.setConnectTimeout(10000);
    conn.setReadTimeout(10000);
    // 与 source.ts 相同的桌面 UA：三端拿到同一套页面模板
    conn.setRequestProperty("User-Agent", SourceRules.USER_AGENT);
    conn.setRequestProperty("Accept", accept);
    conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
    return conn;
  }

  private static byte[] read(HttpURLConnection conn, int max) throws IOException {
    try (InputStream in = conn.getInputStream()) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buf = new byte[8192];
      int n;
      while ((n = in.read(buf)) != -1) {
        if (out.size() + n > max) throw new IOException("response too large");
        out.write(buf, 0, n);
      }
      return out.toByteArray();
    }
  }

  private static SourceResult error(int status, String message) {
    return new SourceResult(status, new byte[0], null, message);
  }
}
