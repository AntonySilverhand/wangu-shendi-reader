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
    System.out.println("Android source rules: " + n + " assertions passed");
  }
}
