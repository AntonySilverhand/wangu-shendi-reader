package org.wanshu.reader;

import java.net.URL;

/**
 * 书源请求白名单（纯 Java，无 Android 依赖，可在 JVM 上单测）。
 * 只允许万书阁上由校验过的书号/章节号组成的路径、站内搜索（POST）与封面图，
 * 不是任意 URL 代理。
 */
public final class SourceRules {
  public static final String USER_AGENT =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";
  public static final String SEARCH_PATH = "/search.html";

  private static final String BOOK = "[1-9][0-9]{0,8}";
  private static final String PAGE_PATH =
      "/book/" + BOOK + "(?:/|/[0-9]{1,12}(?:_[0-9]{1,2})?\\.html|_[0-9]{1,12}\\.html)";
  private static final String COVER_PATH = "/img/" + BOOK + "\\.jpg";
  /** 已 URL 编码的 s=关键词（encodeURIComponent 的输出字符集） */
  private static final String FORM = "s=[A-Za-z0-9%._~!*'()-]{1,400}";

  private SourceRules() {}

  public static boolean isValidBookId(String id) {
    return id != null && id.matches(BOOK);
  }

  public static boolean allowedOrigin(URL url) {
    String protocol = url.getProtocol();
    String host = url.getHost();
    return ("https".equals(protocol) || "http".equals(protocol))
        && ("wanshuge.org".equalsIgnoreCase(host) || "www.wanshuge.org".equalsIgnoreCase(host))
        && url.getUserInfo() == null
        && (url.getPort() == -1 || url.getPort() == url.getDefaultPort());
  }

  /** 目录页/章节页（GET） */
  public static boolean allowedPage(URL url) {
    return allowedOrigin(url) && url.getPath().matches(PAGE_PATH);
  }

  /** 站内搜索（只能 POST，表单只能是 s=已编码关键词） */
  public static boolean allowedSearch(URL url, String form) {
    return allowedOrigin(url) && SEARCH_PATH.equals(url.getPath()) && url.getQuery() == null
        && form != null && form.matches(FORM);
  }

  /** 封面图（GET） */
  public static boolean allowedCover(URL url) {
    return allowedOrigin(url) && url.getPath().matches(COVER_PATH) && url.getQuery() == null;
  }
}
