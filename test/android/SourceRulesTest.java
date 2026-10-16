import java.net.URL;
import org.wanshu.reader.SourceRules;

public final class SourceRulesTest {
  private static int n = 0;

  private static void check(boolean actual, boolean expected, String what) {
    n++;
    if (actual != expected) throw new AssertionError(what + ": expected " + expected);
  }

  private static URL u(String s) throws Exception {
    return new URL(s);
  }

  public static void main(String[] args) throws Exception {
    // 任意合法书号的目录页/章节页
    check(SourceRules.allowedPage(u("https://wanshuge.org/book/36780/")), true, "36780 toc");
    check(SourceRules.allowedPage(u("https://www.wanshuge.org/book/104429/16.html")), true, "toc page");
    check(SourceRules.allowedPage(u("https://wanshuge.org/book/230580_72836267.html")), true, "chapter p0");
    check(SourceRules.allowedPage(u("http://wanshuge.org/book/104429/21930803_1.html")), true, "chapter pN http");
    // 拒绝
    check(SourceRules.allowedPage(u("https://evil.example/book/36780/")), false, "other host");
    check(SourceRules.allowedPage(u("https://wanshuge.org:8443/book/36780/")), false, "port");
    check(SourceRules.allowedPage(u("https://user@wanshuge.org/book/36780/")), false, "userinfo");
    check(SourceRules.allowedPage(u("https://wanshuge.org/book/0/")), false, "book 0");
    check(SourceRules.allowedPage(u("https://wanshuge.org/book/1234567890/")), false, "book too long");
    check(SourceRules.allowedPage(u("https://wanshuge.org/login.html")), false, "login");
    check(SourceRules.allowedPage(u("https://wanshuge.org/search.html")), false, "search via GET");
    check(SourceRules.allowedPage(u("https://wanshuge.org/book/36780/../../etc")), false, "traversal");
    // 搜索：只允许 s=已编码关键词
    URL search = u("https://wanshuge.org/search.html");
    check(SourceRules.allowedSearch(search, "s=%E6%96%97%E7%A0%B4"), true, "search form");
    check(SourceRules.allowedSearch(search, "s=abc"), true, "ascii form");
    check(SourceRules.allowedSearch(search, null), false, "no form");
    check(SourceRules.allowedSearch(search, "s="), false, "empty");
    check(SourceRules.allowedSearch(search, "s=a&x=1"), false, "extra field");
    check(SourceRules.allowedSearch(search, "q=abc"), false, "wrong field");
    check(SourceRules.allowedSearch(u("https://wanshuge.org/search.html?x=1"), "s=a"), false, "query");
    check(SourceRules.allowedSearch(u("https://wanshuge.org/book/36780/"), "s=a"), false, "post elsewhere");
    // 封面
    check(SourceRules.allowedCover(u("https://wanshuge.org/img/104429.jpg")), true, "cover");
    check(SourceRules.allowedCover(u("https://wanshuge.org/img/14/14344.jpg")), false, "cover subdir");
    check(SourceRules.allowedCover(u("https://wanshuge.org/img/x.jpg")), false, "cover bad id");
    check(SourceRules.isValidBookId("36780"), true, "id");
    check(SourceRules.isValidBookId("local-1"), false, "local id");

    // 69书吧：详情页/目录页/章节页
    check(SourceRules.allowedPage(u("https://www.69shuba.com/book/51254.htm")), true, "69 info");
    check(SourceRules.allowedPage(u("https://www.69shuba.com/book/51254/")), true, "69 catalog");
    check(SourceRules.allowedPage(u("https://www.69shuba.com/txt/51254/33489012")), true, "69 chapter");
    check(SourceRules.allowedPage(u("http://69shuba.com/book/51254/")), true, "69 bare host http");
    check(SourceRules.allowedPage(u("https://www.69shuba.com/")), false, "69 home");
    check(SourceRules.allowedPage(u("https://www.69shuba.com/book/51254")), false, "69 no suffix");
    check(SourceRules.allowedPage(u("https://www.69shuba.com/txt/51254/")), false, "69 txt no id");
    check(SourceRules.allowedPage(u("https://www.69shuba.com/book/51254/33489012")), false, "69 wrong chapter path");
    check(SourceRules.allowedPage(u("https://www.69shuba.com/modules/article/search.php")), false, "69 search via GET");
    check(SourceRules.allowedPage(u("https://www.69shuba.com/login.php")), false, "69 login");
    check(SourceRules.allowedPage(u("https://evil.69shuba.com/book/51254/")), false, "69 subdomain");
    check(SourceRules.allowedPage(u("https://www.69shuba.cx/book/51254/")), false, "69 mirror not proxied");
    check(SourceRules.allowedPage(u("https://www.69shuba.com:8443/book/51254/")), false, "69 port");
    check(SourceRules.allowedPage(u("https://wanshuge.org/txt/51254/33489012")), false, "69 path on wanshuge");
    check(SourceRules.allowedPage(u("https://www.69shuba.com/book/36780_38621328.html")), false, "wanshuge path on 69");
    // 69书吧搜索：只允许 searchkey=GBK 编码&searchtype=all
    URL s69 = u("https://www.69shuba.com/modules/article/search.php");
    check(SourceRules.allowedSearch(s69, "searchkey=%CD%F2%B9%C5&searchtype=all"), true, "69 search");
    check(SourceRules.allowedSearch(s69, "searchkey=abc&searchtype=all"), true, "69 ascii search");
    check(SourceRules.allowedSearch(s69, "searchkey=a"), false, "69 missing type");
    check(SourceRules.allowedSearch(s69, "searchkey=a&searchtype=all&x=1"), false, "69 extra field");
    check(SourceRules.allowedSearch(s69, "s=abc"), false, "wanshuge form on 69");
    check(SourceRules.allowedSearch(search, "searchkey=a&searchtype=all"), false, "69 form on wanshuge");
    check(SourceRules.allowedSearch(u("https://www.69shuba.com/search.html"), "s=a"), false, "69 wrong path");
    // 69书吧封面：目录分桶必须等于 书号/1000
    check(SourceRules.allowedCover(u("https://static.69shuba.com/files/article/image/51/51254/51254s.jpg")), true, "69 cover");
    check(SourceRules.allowedCover(u("https://static.69shuba.com/files/article/image/0/999/999s.jpg")), true, "69 cover small id");
    check(SourceRules.allowedCover(u("https://static.69shuba.com/files/article/image/52/51254/51254s.jpg")), false, "69 cover bucket");
    check(SourceRules.allowedCover(u("https://static.69shuba.com/files/article/image/51/51254/51255s.jpg")), false, "69 cover mismatch");
    check(SourceRules.allowedCover(u("https://www.69shuba.com/files/article/image/51/51254/51254s.jpg")), false, "69 cover host");
    check(SourceRules.allowedCover(u("https://static.69shuba.com/files/article/image/51/51254/51254s.jpg?x=1")), false, "69 cover query");
    check("https://static.69shuba.com/files/article/image/51/51254/51254s.jpg".equals(SourceRules.coverUrl("69shuba-51254")), true, "69 cover url");
    check("https://wanshuge.org/img/36780.jpg".equals(SourceRules.coverUrl("36780")), true, "wanshuge cover url");
    check(SourceRules.coverUrl("69shuba-0") == null, true, "69 cover bad id");
    check(SourceRules.coverUrl("../x") == null, true, "cover traversal id");
    check(SourceRules.isShubaBookId("69shuba-51254"), true, "69 id");
    check(SourceRules.isShubaBookId("69shuba-"), false, "69 empty id");
    check(SourceRules.isShubaBookId("51254"), false, "69 id needs prefix");
    // 字符集：Content-Type 优先，其次 meta，未知标签按 utf-8
    check("gbk".equals(SourceRules.charsetOf("text/html; charset=GBK", null)), true, "charset header");
    check("gbk".equals(SourceRules.charsetOf("text/html", "<html><head><meta charset=\"gbk\">".getBytes())), true, "charset meta");
    check("gb2312".equals(SourceRules.charsetOf(null, "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=gb2312\" />".getBytes())), true, "charset http-equiv");
    check("utf-8".equals(SourceRules.charsetOf("text/html", "<html>".getBytes())), true, "charset default");
    check("utf-8".equals(SourceRules.charsetOf("text/html; charset=evil\r\nX: y", null)), true, "charset unknown label");
    System.out.println("Android source rules: " + n + " assertions passed");
  }
}
