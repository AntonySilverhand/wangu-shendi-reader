package org.wanshu.reader;

import android.content.res.AssetManager;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 极简本地 HTTP 服务器（127.0.0.1，随机端口）：
 *  - 静态资源来自 APK assets/www
 *  - /native/source?url=... 代理书源 HTML（限制为 wanshuge.org 的本书路径），
 *    绕开 WebView 的跨域限制；正文解析仍由打包的 JS 完成。
 */
public class LocalServer {
  private static final String[] ALLOWED_HOSTS = {"wanshuge.org", "www.wanshuge.org"};
  private static final int MAX_BODY = 4 * 1024 * 1024;
  private static final String UA =
      "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36";

  private final AssetManager assets;
  private ServerSocket socket;
  private Thread thread;
  volatile boolean running;

  public LocalServer(AssetManager assets) {
    this.assets = assets;
  }

  public void start() throws IOException {
    socket = new ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"));
    running = true;
    thread = new Thread(new AcceptLoop(this), "reader-server");
    thread.setDaemon(true);
    thread.start();
  }

  boolean isRunning() {
    return running;
  }

  Socket accept() throws IOException {
    return socket.accept();
  }

  public int getPort() {
    return socket == null ? 0 : socket.getLocalPort();
  }

  public void stop() {
    running = false;
    try {
      if (socket != null) socket.close();
    } catch (IOException ignored) {
    }
  }

  void handle(Socket client) {
    try (Socket c = client) {
      c.setSoTimeout(20000);
      InputStream in = new BufferedInputStream(c.getInputStream());
      OutputStream out = new BufferedOutputStream(c.getOutputStream());
      String requestLine = readLine(in);
      if (requestLine == null || requestLine.isEmpty()) return;
      String[] parts = requestLine.split(" ");
      String method = parts.length > 0 ? parts[0] : "GET";
      String target = parts.length > 1 ? parts[1] : "/";
      // 读完请求头
      String line;
      int headerCount = 0;
      while ((line = readLine(in)) != null && !line.isEmpty() && headerCount++ < 64) {
        // 忽略
      }
      if (!"GET".equals(method) && !"HEAD".equals(method)) {
        respond(out, 405, "text/plain; charset=utf-8", "Method Not Allowed".getBytes(StandardCharsets.UTF_8), null, method);
        return;
      }
      String path = target;
      String query = "";
      int q = target.indexOf('?');
      if (q >= 0) {
        path = target.substring(0, q);
        query = target.substring(q + 1);
      }
      path = URLDecoder.decode(path, "UTF-8");

      if (path.equals("/native/source")) {
        handleSource(out, query, method);
        return;
      }
      serveAsset(out, path, method);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void handleSource(OutputStream out, String query, String method) throws IOException {
    String raw = param(query, "url");
    if (raw == null || raw.isEmpty()) {
      respond(out, 400, "text/plain; charset=utf-8", "missing url".getBytes(StandardCharsets.UTF_8), null, method);
      return;
    }
    URL url;
    try {
      url = new URL(raw);
    } catch (Exception e) {
      respond(out, 400, "text/plain; charset=utf-8", "bad url".getBytes(StandardCharsets.UTF_8), null, method);
      return;
    }
    String host = url.getHost().toLowerCase(Locale.ROOT);
    boolean allowedHost = false;
    for (String h : ALLOWED_HOSTS) if (h.equals(host)) allowedHost = true;
    if (!allowedHost || !url.getPath().startsWith("/book/36780")) {
      respond(out, 403, "text/plain; charset=utf-8", "forbidden".getBytes(StandardCharsets.UTF_8), null, method);
      return;
    }

    HttpURLConnection conn = null;
    try {
      conn = (HttpURLConnection) url.openConnection();
      conn.setInstanceFollowRedirects(true);
      conn.setConnectTimeout(12000);
      conn.setReadTimeout(12000);
      conn.setRequestProperty("User-Agent", UA);
      conn.setRequestProperty("Accept", "text/html,application/xhtml+xml");
      conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
      int status = conn.getResponseCode();
      if (status != 200) {
        respond(out, 502, "text/plain; charset=utf-8",
            ("upstream HTTP " + status).getBytes(StandardCharsets.UTF_8), String.valueOf(conn.getURL()), method);
        return;
      }
      String finalHost = conn.getURL().getHost().toLowerCase(Locale.ROOT);
      boolean finalAllowed = false;
      for (String h : ALLOWED_HOSTS) if (h.equals(finalHost)) finalAllowed = true;
      if (!finalAllowed) {
        respond(out, 502, "text/plain; charset=utf-8", "redirected off host".getBytes(StandardCharsets.UTF_8), null, method);
        return;
      }
      InputStream body = new BufferedInputStream(conn.getInputStream());
      byte[] data = readLimited(body, MAX_BODY);
      respond(out, 200, "text/html; charset=utf-8", data, conn.getURL().toString(), method);
    } catch (IOException e) {
      respond(out, 502, "text/plain; charset=utf-8",
          ("fetch failed: " + e.getMessage()).getBytes(StandardCharsets.UTF_8), null, method);
    } finally {
      if (conn != null) conn.disconnect();
    }
  }

  private void serveAsset(OutputStream out, String path, String method) throws IOException {
    String clean = path.replace("..", "").replaceAll("//+", "/");
    if (clean.equals("/") || clean.isEmpty()) clean = "/index.html";
    String assetPath = "www" + clean;
    InputStream stream = null;
    try {
      stream = assets.open(assetPath);
    } catch (IOException e) {
      // SPA 回退
      try {
        stream = assets.open("www/index.html");
        assetPath = "www/index.html";
      } catch (IOException e2) {
        respond(out, 404, "text/plain; charset=utf-8", "not found".getBytes(StandardCharsets.UTF_8), null, method);
        return;
      }
    }
    byte[] data = readLimited(new BufferedInputStream(stream), MAX_BODY);
    respond(out, 200, mimeOf(assetPath), data, null, method);
  }

  private static String mimeOf(String path) {
    String p = path.toLowerCase(Locale.ROOT);
    if (p.endsWith(".html")) return "text/html; charset=utf-8";
    if (p.endsWith(".js")) return "text/javascript; charset=utf-8";
    if (p.endsWith(".css")) return "text/css; charset=utf-8";
    if (p.endsWith(".json") || p.endsWith(".webmanifest")) return "application/json; charset=utf-8";
    if (p.endsWith(".svg")) return "image/svg+xml";
    if (p.endsWith(".png")) return "image/png";
    if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
    if (p.endsWith(".webp")) return "image/webp";
    if (p.endsWith(".ico")) return "image/x-icon";
    if (p.endsWith(".txt")) return "text/plain; charset=utf-8";
    return "application/octet-stream";
  }

  private static void respond(OutputStream out, int status, String contentType, byte[] body,
                              String finalUrl, String method) throws IOException {
    Map<String, String> headers = new HashMap<>();
    headers.put("Content-Type", contentType);
    headers.put("Cache-Control", "no-store");
    headers.put("Access-Control-Allow-Origin", "*");
    if (finalUrl != null) headers.put("X-Final-Url", finalUrl);
    StringBuilder head = new StringBuilder();
    head.append("HTTP/1.1 ").append(status).append(' ').append(statusText(status)).append("\r\n");
    for (Map.Entry<String, String> e : headers.entrySet()) {
      head.append(e.getKey()).append(": ").append(e.getValue()).append("\r\n");
    }
    head.append("Content-Length: ").append(body.length).append("\r\n");
    head.append("Connection: close\r\n\r\n");
    out.write(head.toString().getBytes(StandardCharsets.UTF_8));
    if (!"HEAD".equals(method)) out.write(body);
    out.flush();
  }

  private static String statusText(int status) {
    switch (status) {
      case 200: return "OK";
      case 400: return "Bad Request";
      case 403: return "Forbidden";
      case 404: return "Not Found";
      case 405: return "Method Not Allowed";
      default: return "Bad Gateway";
    }
  }

  private static String param(String query, String name) throws IOException {
    for (String pair : query.split("&")) {
      int eq = pair.indexOf('=');
      if (eq > 0 && pair.substring(0, eq).equals(name)) {
        return URLDecoder.decode(pair.substring(eq + 1), "UTF-8");
      }
    }
    return null;
  }

  private static byte[] readLimited(InputStream in, int limit) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    byte[] chunk = new byte[8192];
    int n;
    int total = 0;
    while ((n = in.read(chunk)) > 0) {
      total += n;
      if (total > limit) throw new IOException("response too large");
      buffer.write(chunk, 0, n);
    }
    return buffer.toByteArray();
  }

  private static String readLine(InputStream in) throws IOException {
    ByteArrayOutputStream line = new ByteArrayOutputStream();
    int c;
    while ((c = in.read()) != -1) {
      if (c == '\n') break;
      if (c != '\r') line.write(c);
      if (line.size() > 8192) break;
    }
    if (c == -1 && line.size() == 0) return null;
    return new String(line.toByteArray(), StandardCharsets.UTF_8);
  }
}
