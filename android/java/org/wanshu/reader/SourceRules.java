package org.wanshu.reader;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 书源请求白名单（纯 Java，无 Android 依赖，可在 JVM 上单测）。
 * 只允许由校验过的书号/章节号组成的路径、站内搜索（POST）与封面图，不是任意 URL 代理：
 *  - 万书阁：/book/{b}/、/book/{b}/{n}.html、/book/{b}_{id}.html、/book/{b}/{id}_{n}.html、
 *    POST /search.html（s=）、/img/{b}.jpg；
 *  - 69书吧：/book/{b}.htm、/book/{b}/、/txt/{b}/{id}、
 *    POST /modules/article/search.php（searchkey=GBK 编码&searchtype=all）、
 *    static.69shuba.com/files/article/image/{b/1000}/{b}/{b}s.jpg；
 *  - 爱下电子书（整本下载型）：书页 /read/{b}/、GET /bsearch?q=已编码关键词、
 *    整本压缩包 down7.ixdzs8.com/{b}.zip（章节页有安全验证，不访问）；
 *  - 飘天文学：/bookinfo/{a}/{b}.html、/html/{a}/{b}/（含 index.html）、/html/{a}/{b}/{id}.html（{a} = b/1000）、
 *    POST /modules/article/search.php（searchtype=articlename&searchkey=GBK 编码）、/files/article/image/{a}/{b}/{b}s.jpg；
 *  - 露露书：/{a}_{b}/、/{a}_{b}/{id}.html（{a} = b/1000）、POST /home/search（action=search&q=）、
 *    POST /home/index/ajaxchapter（id=&eKey=十六进制&cid=&basecid=，可选 nextpage/pagehash）、/uploads/{a}/{b}.jpg。
 * 与 src/shared/source.ts、source-69shuba.ts、source-ixdzs.ts、source-piaotia.ts、source-lulushu.ts 保持同一白名单。
 */
public final class SourceRules {
  public static final String USER_AGENT =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";
  public static final String SEARCH_PATH = "/search.html";
  public static final String SHUBA_SEARCH_PATH = "/modules/article/search.php";
  public static final String SHUBA_PREFIX = "69shuba-";
  public static final String IXDZS_PREFIX = "ixdzs-";
  public static final String IXDZS_SEARCH_PATH = "/bsearch";
  public static final String PIAOTIA_PREFIX = "piaotia-";
  public static final String LULUSHU_PREFIX = "lulushu-";
  public static final String LULUSHU_SEARCH_PATH = "/home/search";
  public static final String LULUSHU_AJAX_PATH = "/home/index/ajaxchapter";

  private static final String BOOK = "[1-9][0-9]{0,8}";
  private static final String PAGE_PATH =
      "/book/" + BOOK + "(?:/|/[0-9]{1,12}(?:_[0-9]{1,2})?\\.html|_[0-9]{1,12}\\.html)";
  private static final String COVER_PATH = "/img/" + BOOK + "\\.jpg";
  /** 已 URL 编码的 s=关键词（encodeURIComponent 的输出字符集） */
  private static final String FORM = "s=[A-Za-z0-9%._~!*'()-]{1,400}";

  private static final String SHUBA_PAGE_PATH =
      "/book/" + BOOK + "(?:\\.htm|/)|/txt/" + BOOK + "/[0-9]{1,12}";
  /** GBK 百分号编码的关键词（GBK 之外的字符以 %26%23码位%3B 提交，故放宽长度） */
  private static final String SHUBA_FORM = "searchkey=[A-Za-z0-9%._~!*'()-]{1,600}&searchtype=all";
  private static final Pattern SHUBA_COVER_PATH =
      Pattern.compile("/files/article/image/([0-9]{1,6})/(" + BOOK + ")/([0-9]{1,9})s\\.jpg");
  private static final Pattern SHUBA_BOOK_ID = Pattern.compile("69shuba-(" + BOOK + ")");
  private static final Pattern IXDZS_BOOK_ID = Pattern.compile("ixdzs-(" + BOOK + ")");
  private static final String IXDZS_PAGE_PATH = "/read/" + BOOK + "/";
  private static final String IXDZS_SEARCH_QUERY = "q=[A-Za-z0-9%._~!*'()-]{1,400}";
  private static final String IXDZS_DOWNLOAD_PATH = "/" + BOOK + "\\.zip";
  private static final Pattern PIAOTIA_BOOK_ID = Pattern.compile("piaotia-(" + BOOK + ")");
  /** group(1) = 分片 a，group(2) = 书号 b（调用方校验 a == b/1000） */
  private static final Pattern PIAOTIA_PAGE_PATH = Pattern.compile(
      "/(?:bookinfo/([0-9]{1,6})/(" + BOOK + ")\\.html|html/([0-9]{1,6})/(" + BOOK + ")/(?:index\\.html|[0-9]{1,12}\\.html)?)");
  private static final Pattern PIAOTIA_COVER_PATH =
      Pattern.compile("/files/article/image/([0-9]{1,6})/(" + BOOK + ")/([0-9]{1,9})s\\.jpg");
  private static final String PIAOTIA_FORM = "searchtype=articlename&searchkey=[A-Za-z0-9%._~!*'()-]{1,600}";
  private static final Pattern LULUSHU_BOOK_ID = Pattern.compile("lulushu-(" + BOOK + ")");
  /** group(1) = 分片 a，group(2) = 书号 b */
  private static final Pattern LULUSHU_PAGE_PATH = Pattern.compile("/([0-9]{1,6})_(" + BOOK + ")/(?:[0-9]{1,12}\\.html)?");
  private static final Pattern LULUSHU_COVER_PATH = Pattern.compile("/uploads/([0-9]{1,6})/(" + BOOK + ")\\.jpg");
  private static final String LULUSHU_SEARCH_FORM = "action=search&q=[A-Za-z0-9%._~!*'()-]{1,400}";
  private static final String LULUSHU_AJAX_FORM = "id=" + BOOK + "&eKey=[0-9a-f]{8,64}&cid=([0-9]{1,12})&basecid=([0-9]{1,12})"
      + "(?:&nextpage=[0-9]{1,4}&pagehash=[0-9a-f]{8,64})?";
  private static final Pattern CHARSET = Pattern.compile("charset\\s*=\\s*[\"']?([A-Za-z0-9_-]+)",
      Pattern.CASE_INSENSITIVE);
  private static final Pattern META_CHARSET = Pattern.compile(
      "<meta[^>]+charset\\s*=\\s*[\"']?([A-Za-z0-9_-]+)", Pattern.CASE_INSENSITIVE);

  private SourceRules() {}

  /** 万书阁书号（纯数字） */
  public static boolean isValidBookId(String id) {
    return id != null && id.matches(BOOK);
  }

  /** 69书吧书号（69shuba-数字） */
  public static boolean isShubaBookId(String id) {
    return id != null && SHUBA_BOOK_ID.matcher(id).matches();
  }

  private static boolean plainOrigin(URL url, String host) {
    String protocol = url.getProtocol();
    return ("https".equals(protocol) || "http".equals(protocol))
        && host.equalsIgnoreCase(url.getHost())
        && url.getUserInfo() == null
        && (url.getPort() == -1 || url.getPort() == url.getDefaultPort());
  }

  public static boolean allowedOrigin(URL url) {
    return plainOrigin(url, "wanshuge.org") || plainOrigin(url, "www.wanshuge.org");
  }

  public static boolean allowedShubaOrigin(URL url) {
    return plainOrigin(url, "www.69shuba.com") || plainOrigin(url, "69shuba.com");
  }

  public static boolean allowedIxdzsOrigin(URL url) {
    return plainOrigin(url, "ixdzs8.com") || plainOrigin(url, "www.ixdzs8.com");
  }

  public static boolean allowedPiaotiaOrigin(URL url) {
    return plainOrigin(url, "www.piaotia.com") || plainOrigin(url, "piaotia.com");
  }

  public static boolean allowedLulushuOrigin(URL url) {
    return plainOrigin(url, "www.lulushu55.com") || plainOrigin(url, "lulushu55.com");
  }

  /** 露露书书号（lulushu-数字） */
  public static boolean isLulushuBookId(String id) {
    return id != null && LULUSHU_BOOK_ID.matcher(id).matches();
  }

  private static boolean allowedLulushuPage(URL url) {
    if (url.getQuery() != null) return false;
    Matcher m = LULUSHU_PAGE_PATH.matcher(url.getPath());
    return m.matches() && shardMatches(m.group(1), m.group(2));
  }

  /** 飘天文学书号（piaotia-数字） */
  public static boolean isPiaotiaBookId(String id) {
    return id != null && PIAOTIA_BOOK_ID.matcher(id).matches();
  }

  private static boolean shardMatches(String shard, String book) {
    return Long.parseLong(shard) == Long.parseLong(book) / 1000;
  }

  private static boolean allowedPiaotiaPage(URL url) {
    if (url.getQuery() != null) return false;
    Matcher m = PIAOTIA_PAGE_PATH.matcher(url.getPath());
    if (!m.matches()) return false;
    return m.group(1) != null ? shardMatches(m.group(1), m.group(2)) : shardMatches(m.group(3), m.group(4));
  }

  /** 爱下电子书书号（ixdzs-数字） */
  public static boolean isIxdzsBookId(String id) {
    return id != null && IXDZS_BOOK_ID.matcher(id).matches();
  }

  /** 目录页/详情页/章节页（GET） */
  public static boolean allowedPage(URL url) {
    if (allowedOrigin(url)) return url.getPath().matches(PAGE_PATH);
    if (allowedShubaOrigin(url)) return url.getPath().matches(SHUBA_PAGE_PATH);
    if (allowedIxdzsOrigin(url)) return url.getQuery() == null && url.getPath().matches(IXDZS_PAGE_PATH);
    if (allowedPiaotiaOrigin(url)) return allowedPiaotiaPage(url);
    if (allowedLulushuOrigin(url)) return allowedLulushuPage(url);
    return false;
  }

  /** GET 搜索（爱下电子书 /bsearch?q=已编码关键词，不允许其它参数） */
  public static boolean allowedGetSearch(URL url) {
    return allowedIxdzsOrigin(url) && IXDZS_SEARCH_PATH.equals(url.getPath())
        && url.getQuery() != null && url.getQuery().matches(IXDZS_SEARCH_QUERY);
  }

  /** 书号 → 整本压缩包地址；不支持整本下载的书号返回 null */
  public static String downloadUrl(String bookId) {
    if (!isIxdzsBookId(bookId)) return null;
    return "https://down7.ixdzs8.com/" + bookId.substring(IXDZS_PREFIX.length()) + ".zip";
  }

  /** 整本压缩包（GET，只允许书号拼出的路径） */
  public static boolean allowedDownload(URL url) {
    return plainOrigin(url, "down7.ixdzs8.com") && url.getQuery() == null
        && url.getPath().matches(IXDZS_DOWNLOAD_PATH);
  }

  /** 站内 POST：搜索（表单只能是已编码的关键词字段）与露露书正文接口（只能是数字书号/章节号 + 十六进制校验码） */
  public static boolean allowedSearch(URL url, String form) {
    if (form == null || url.getQuery() != null) return false;
    if (allowedOrigin(url)) return SEARCH_PATH.equals(url.getPath()) && form.matches(FORM);
    if (allowedShubaOrigin(url)) return SHUBA_SEARCH_PATH.equals(url.getPath()) && form.matches(SHUBA_FORM);
    if (allowedPiaotiaOrigin(url)) return SHUBA_SEARCH_PATH.equals(url.getPath()) && form.matches(PIAOTIA_FORM);
    if (allowedLulushuOrigin(url)) {
      if (LULUSHU_SEARCH_PATH.equals(url.getPath())) return form.matches(LULUSHU_SEARCH_FORM);
      if (!LULUSHU_AJAX_PATH.equals(url.getPath())) return false;
      Matcher m = Pattern.compile(LULUSHU_AJAX_FORM).matcher(form);
      return m.matches() && m.group(1).equals(m.group(2));
    }
    return false;
  }

  /** 封面图（GET） */
  public static boolean allowedCover(URL url) {
    if (url.getQuery() != null) return false;
    if (allowedOrigin(url)) return url.getPath().matches(COVER_PATH);
    if (plainOrigin(url, "static.69shuba.com")) {
      Matcher m = SHUBA_COVER_PATH.matcher(url.getPath());
      if (!m.matches() || !m.group(2).equals(m.group(3))) return false;
      return Long.parseLong(m.group(1)) == Long.parseLong(m.group(2)) / 1000;
    }
    if (plainOrigin(url, "www.piaotia.com")) {
      Matcher m = PIAOTIA_COVER_PATH.matcher(url.getPath());
      return m.matches() && m.group(2).equals(m.group(3)) && shardMatches(m.group(1), m.group(2));
    }
    if (plainOrigin(url, "www.lulushu55.com")) {
      Matcher m = LULUSHU_COVER_PATH.matcher(url.getPath());
      return m.matches() && shardMatches(m.group(1), m.group(2));
    }
    return false;
  }

  /** 书号 → 封面地址；非法书号返回 null */
  public static String coverUrl(String bookId) {
    if (isValidBookId(bookId)) return "https://wanshuge.org/img/" + bookId + ".jpg";
    if (isShubaBookId(bookId)) {
      String n = bookId.substring(SHUBA_PREFIX.length());
      return "https://static.69shuba.com/files/article/image/" + (Long.parseLong(n) / 1000)
          + "/" + n + "/" + n + "s.jpg";
    }
    if (isPiaotiaBookId(bookId)) {
      String n = bookId.substring(PIAOTIA_PREFIX.length());
      return "https://www.piaotia.com/files/article/image/" + (Long.parseLong(n) / 1000)
          + "/" + n + "/" + n + "s.jpg";
    }
    if (isLulushuBookId(bookId)) {
      String n = bookId.substring(LULUSHU_PREFIX.length());
      return "https://www.lulushu55.com/uploads/" + (Long.parseLong(n) / 1000) + "/" + n + ".jpg";
    }
    return null;
  }

  /**
   * 上游页面的字符集：Content-Type 优先，其次页面头部 meta，默认 utf-8。
   * 只返回已知标签，原始字节原样交给 JS（source.ts 按此字符集解码）。
   */
  public static String charsetOf(String contentType, byte[] body) {
    String label = knownCharset(contentType, CHARSET);
    if (label == null && body != null) {
      int len = Math.min(body.length, 2048);
      label = knownCharset(new String(body, 0, len, StandardCharsets.ISO_8859_1), META_CHARSET);
    }
    return label == null ? "utf-8" : label;
  }

  private static String knownCharset(String text, Pattern pattern) {
    if (text == null) return null;
    Matcher m = pattern.matcher(text);
    if (!m.find()) return null;
    String label = m.group(1).toLowerCase(Locale.ROOT);
    if ("utf8".equals(label)) return "utf-8";
    if ("utf-8".equals(label) || "gbk".equals(label) || "gb2312".equals(label)
        || "gb18030".equals(label)) {
      return label;
    }
    return null;
  }
}
