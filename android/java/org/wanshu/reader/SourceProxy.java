package org.wanshu.reader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** 请求由 JS source.ts 队列调度；原生仅提供受限传输，不自行重试。 */
final class SourceProxy {
  private static final int MAX_BODY = 4 * 1024 * 1024;

  static boolean allowed(URL url) {
    String protocol = url.getProtocol();
    String host = url.getHost();
    return ("https".equals(protocol) || "http".equals(protocol))
        && ("wanshuge.org".equalsIgnoreCase(host) || "www.wanshuge.org".equalsIgnoreCase(host))
        && url.getUserInfo() == null
        && (url.getPort() == -1 || url.getPort() == url.getDefaultPort())
        && url.getPath().matches("/book/36780(?:/|/[0-9]{1,12}(?:_[0-9]{1,2})?\\.html|_[0-9]{1,12}\\.html)");
  }

  SourceResult fetch(String rawUrl) {
    try {
      URL url = new URL(rawUrl);
      // 每一跳都先验证，不跟随后再检查（禁止向任意地址发请求）。
      for (int hop = 0; hop < 5; hop++) {
        if (!allowed(url)) return error(403, "forbidden");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
          conn.setInstanceFollowRedirects(false);
          conn.setConnectTimeout(10000);
          conn.setReadTimeout(10000);
          conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0 Mobile Safari/537.36");
          conn.setRequestProperty("Accept", "text/html,application/xhtml+xml");
          int status = conn.getResponseCode();
          if (status >= 300 && status <= 399) {
            String location = conn.getHeaderField("Location");
            if (location == null) return error(502, "redirect without location");
            url = new URL(url, location);
            continue;
          }
          // 保留 404：章节分页探针依赖它作为终章证据。
          if (status != 200) return error(status >= 400 && status <= 599 ? status : 502, "upstream HTTP " + status);
          try (InputStream in = conn.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
              if (out.size() + n > MAX_BODY) throw new IOException("response too large");
              out.write(buf, 0, n);
            }
            return new SourceResult(200, out.toByteArray(), url.toString(), null);
          }
        } finally {
          conn.disconnect();
        }
      }
      return error(502, "too many redirects");
    } catch (Exception e) {
      return error(502, "fetch failed: " + e.getMessage());
    }
  }

  private static SourceResult error(int status, String message) {
    return new SourceResult(status, new byte[0], null, message);
  }
}
