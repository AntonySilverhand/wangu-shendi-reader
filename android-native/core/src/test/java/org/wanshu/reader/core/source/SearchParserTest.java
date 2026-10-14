package org.wanshu.reader.core.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.wanshu.reader.core.model.BookInfo;
import org.wanshu.reader.core.model.BookSummary;
import org.wanshu.reader.core.net.FakeHttpConnection;
import org.wanshu.reader.core.net.FakeHttpConnectionFactory;
import org.wanshu.reader.core.net.SourceHttpClient;

public class SearchParserTest {

    private static final String SEARCH = "https://wanshuge.org/search.html";

    private static String fixture(String name) {
        return FixtureHelper.readFixture(name);
    }

    private static BookSummary byId(List<BookSummary> list, String id) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(id)) {
                return list.get(i);
            }
        }
        return null;
    }

    @Test
    public void desktopTemplate() {
        List<BookSummary> r = SourceParser.parseSearchResults(fixture("search-desktop.html"));
        assertNotNull(r);
        assertTrue(r.size() > 20);
        BookSummary first = r.get(0);
        assertEquals("17542", first.getId());
        assertEquals("斗破苍穹之重生萧玄", first.getTitle());
        assertEquals("爱蹦迪的奶牛", first.getAuthor());
        assertEquals("言情小说", first.getCategory());
        assertEquals("第六章 富贵险中求", first.getLatestChapter());
        assertEquals("08-07", first.getUpdated());
        assertNull(first.getIntro());
        BookSummary dpcq = byId(r, "104429");
        assertNotNull(dpcq);
        assertEquals("斗破苍穹", dpcq.getTitle());
        Set<String> ids = new HashSet<String>();
        for (int i = 0; i < r.size(); i++) {
            ids.add(r.get(i).getId());
        }
        assertEquals("ids are unique", r.size(), ids.size());
    }

    @Test
    public void mobileTemplateOnlyTakesTheResultRegion() {
        List<BookSummary> r = SourceParser.parseSearchResults(fixture("search-mobile.html"));
        assertNotNull(r);
        assertTrue(r.size() > 10);
        assertTrue(r.size() <= SourceParser.MAX_SEARCH_RESULTS);
        assertEquals("14455", r.get(0).getId());
        assertNotNull(r.get(0).getIntro());
        assertTrue(r.get(0).getIntro().startsWith("穿越斗破"));
    }

    @Test
    public void noContainerIsNullAndEmptyListIsEmpty() {
        assertNull(SourceParser.parseSearchResults("<html><body>blocked</body></html>"));
        List<BookSummary> empty = SourceParser.parseSearchResults("<ul class=\"txt-list\" id=\"novel-search\">请输入搜索词</ul>");
        assertNotNull(empty);
        assertTrue(empty.isEmpty());
    }

    @Test
    public void resultsAreCappedAndDeduplicated() {
        StringBuilder sb = new StringBuilder("<ul id=\"novel-search\">");
        for (int i = 1; i <= 80; i++) {
            int id = 1000 + (i % 70);
            sb.append("<li><span class=\"s2\"><a href=\"/book/").append(id).append("/\">书").append(id).append("</a></span></li>");
        }
        sb.append("</ul>");
        List<BookSummary> r = SourceParser.parseSearchResults(sb.toString());
        assertNotNull(r);
        assertEquals(SourceParser.MAX_SEARCH_RESULTS, r.size());
        Set<String> ids = new HashSet<String>();
        for (int i = 0; i < r.size(); i++) {
            ids.add(r.get(i).getId());
        }
        assertEquals(r.size(), ids.size());
    }

    @Test
    public void linksThatAreNotBookPagesAreIgnored() {
        String html = "<ul id=\"novel-search\">"
                + "<li><span class=\"s2\"><a href=\"https://evil.com/book/5/\">坏</a></span></li>"
                + "<li><span class=\"s2\"><a href=\"/book/012/\">坏2</a></span></li>"
                + "<li><span class=\"s2\"><a href=\"/book/77/\">好</a></span></li>"
                + "</ul>";
        List<BookSummary> r = SourceParser.parseSearchResults(html);
        assertNotNull(r);
        assertEquals(1, r.size());
        assertEquals("77", r.get(0).getId());
    }

    @Test
    public void queryNormalization() {
        assertEquals("斗破 苍穹", SourceParser.normalizeSearchQuery("  斗破   苍穹 "));
        assertEquals("斗破 苍穹", SourceParser.normalizeSearchQuery("斗破　苍穹"));
        assertNull(SourceParser.normalizeSearchQuery(""));
        assertNull(SourceParser.normalizeSearchQuery("   "));
        assertNull(SourceParser.normalizeSearchQuery(null));
        assertNull(SourceParser.normalizeSearchQuery("a\u0000b"));
        assertNull(SourceParser.normalizeSearchQuery("a\nb"));
        StringBuilder over = new StringBuilder();
        StringBuilder exact = new StringBuilder();
        for (int i = 0; i < 31; i++) {
            over.append('字');
            if (i < 30) {
                exact.append('字');
            }
        }
        assertNull(SourceParser.normalizeSearchQuery(over.toString()));
        assertEquals(exact.toString(), SourceParser.normalizeSearchQuery(exact.toString()));
    }

    @Test
    public void searchActionPostsTheFormAndParsesResults() throws Exception {
        FakeHttpConnectionFactory factory = new FakeHttpConnectionFactory();
        HashMap<String, String> headers = new HashMap<String, String>();
        headers.put("Content-Type", "text/html; charset=utf-8");
        factory.addResponse("POST", SEARCH, 200, fixture("search-desktop.html").getBytes(StandardCharsets.UTF_8), headers);

        String query = SourceParser.normalizeSearchQuery(" 斗破苍穹 ");
        SearchBooksAction action = new SearchBooksAction(new SourceHttpClient(factory, false), null, query);
        List<BookSummary> r = action.execute(null);

        assertTrue(r.size() > 20);
        FakeHttpConnection conn = factory.getOpenedConnections().get(0);
        assertEquals("POST", conn.getRecordedMethod());
        assertEquals("s=" + "%E6%96%97%E7%A0%B4%E8%8B%8D%E7%A9%B9", new String(conn.getWrittenBody(), StandardCharsets.UTF_8));
        assertEquals("application/x-www-form-urlencoded", conn.getRequestHeader("Content-Type"));
    }

    @Test
    public void searchActionEncodesSpacesAsPercent20() {
        assertEquals("s=a%20b", SearchBooksAction.formBody("a b"));
    }

    @Test
    public void searchActionFailsOnUnexpectedPage() throws Exception {
        FakeHttpConnectionFactory factory = new FakeHttpConnectionFactory();
        factory.addResponse("POST", SEARCH, 200, "<html>blocked</html>".getBytes(StandardCharsets.UTF_8), null);
        SearchBooksAction action = new SearchBooksAction(new SourceHttpClient(factory, false), null, "斗破");
        try {
            action.execute(null);
            fail("an unrecognised page must be an error, not an empty result");
        } catch (IOException expected) {}
    }

    @Test
    public void searchActionRejectsInvalidQuery() throws Exception {
        FakeHttpConnectionFactory factory = new FakeHttpConnectionFactory();
        SearchBooksAction action = new SearchBooksAction(new SourceHttpClient(factory, false), null, "  ");
        try {
            action.execute(null);
            fail("blank query must be rejected");
        } catch (IllegalArgumentException expected) {}
        assertEquals(0, factory.getRequestedUrls().size());
    }

    @Test
    public void bookInfoActionReadsDetailPage() throws Exception {
        FakeHttpConnectionFactory factory = new FakeHttpConnectionFactory();
        HashMap<String, String> headers = new HashMap<String, String>();
        headers.put("Content-Type", "text/html; charset=utf-8");
        factory.addResponse("https://wanshuge.org/book/104429/", 200, fixture("book-104429-p1.html").getBytes(StandardCharsets.UTF_8), headers);
        BookInfo info = new FetchBookInfoAction("104429", new SourceHttpClient(factory, false)).execute(null);
        assertEquals("斗破苍穹", info.getTitle());
        assertEquals("21930717", info.getFirstChapterId());
    }

    @Test
    public void coverActionReturnsImageBytes() throws Exception {
        FakeHttpConnectionFactory factory = new FakeHttpConnectionFactory();
        HashMap<String, String> headers = new HashMap<String, String>();
        headers.put("Content-Type", "image/jpeg");
        byte[] jpeg = new byte[] {(byte) 0xFF, (byte) 0xD8, 1, 2, 3};
        factory.addResponse("https://wanshuge.org/img/104429.jpg", 200, jpeg, headers);
        byte[] got = new FetchCoverBytesAction("104429", new SourceHttpClient(factory, false)).execute(null);
        assertEquals(jpeg.length, got.length);
        factory.addResponse("https://wanshuge.org/img/1.jpg", 404, new byte[0], headers);
        try {
            new FetchCoverBytesAction("1", new SourceHttpClient(factory, false)).execute(null);
            fail("missing cover must be an error");
        } catch (IOException expected) {}
    }
}
