package org.wanshu.reader;

import android.content.res.AssetManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 从 APK assets/www 读取静态资源（顶层类，禁止内部类/lambda）。
 */
final class AssetProvider {
  private static final int MAX_ASSET = 8 * 1024 * 1024;

  private final AssetManager assets;

  AssetProvider(AssetManager assets) {
    this.assets = assets;
  }

  /** 打开资源；不存在时返回 null。路径已由调用方清理。 */
  byte[] open(String path) throws IOException {
    String clean = path.replace("..", "").replaceAll("//+", "/");
    if (clean.equals("/") || clean.isEmpty()) clean = "/index.html";
    InputStream stream = null;
    try {
      stream = assets.open("www" + clean);
    } catch (IOException notFound) {
      return null;
    }
    try {
      return readLimited(stream, MAX_ASSET);
    } finally {
      stream.close();
    }
  }

  static String mimeOf(String path) {
    String p = path.toLowerCase(Locale.ROOT);
    if (p.endsWith(".html")) return "text/html";
    if (p.endsWith(".js")) return "text/javascript";
    if (p.endsWith(".mjs")) return "text/javascript";
    if (p.endsWith(".css")) return "text/css";
    if (p.endsWith(".json") || p.endsWith(".webmanifest")) return "application/manifest+json";
    if (p.endsWith(".svg")) return "image/svg+xml";
    if (p.endsWith(".png")) return "image/png";
    if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
    if (p.endsWith(".webp")) return "image/webp";
    if (p.endsWith(".ico")) return "image/x-icon";
    if (p.endsWith(".txt")) return "text/plain";
    if (p.endsWith(".woff2")) return "font/woff2";
    return "application/octet-stream";
  }

  private static byte[] readLimited(InputStream in, int limit) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    byte[] chunk = new byte[8192];
    int n;
    int total = 0;
    while ((n = in.read(chunk)) > 0) {
      total += n;
      if (total > limit) throw new IOException("asset too large");
      buffer.write(chunk, 0, n);
    }
    return buffer.toByteArray();
  }
}
