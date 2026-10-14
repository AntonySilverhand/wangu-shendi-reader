package org.wanshu.reader.core.source;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.parser.Parser;
import org.wanshu.reader.core.model.BookInfo;
import org.wanshu.reader.core.model.BookSummary;
import org.wanshu.reader.core.model.TocEntry;
import org.wanshu.reader.core.util.Base64Decoder;

public class SourceParser {

    public static final int MAX_SEARCH_RESULTS = 50;
    public static final int MAX_SEARCH_QUERY_CHARS = 30;

    private static final Pattern NOISE_PATTERNS[] = new Pattern[] {
            Pattern.compile("^请勿开启浏览器阅读模式"),
            Pattern.compile("^手机浏览器扫描二维码"),
            Pattern.compile("^万书阁"),
            Pattern.compile("^加入书架$"),
            Pattern.compile("^保存书签$"),
            Pattern.compile("^上一章$"),
            Pattern.compile("^下一章$"),
            Pattern.compile("^章节(目录|列表)$"),
            Pattern.compile("^热门小说推荐"),
            Pattern.compile("^阅读记录$"),
            Pattern.compile("^后续内容已被隐藏"),
            Pattern.compile("^如果您已经是VIP会员")
    };

    /** The source's member-only notice; detected so the reader can say so (never bypassed). */
    private static final Pattern RESTRICTED_PATTERN = Pattern.compile("后续内容已被隐藏|请升级VIP会员后继续阅读");

    private static final Pattern QSBS_PATTERN = Pattern.compile("qsbs\\.bb\\('([^']*)'\\)");
    private static final Pattern H2_TITLE_PATTERN = Pattern.compile("<h2[^>]*class=\"[^\"]*chapter-title[^\"]*\"[^>]*>([\\s\\S]*?)</h2>", Pattern.CASE_INSENSITIVE);
    private static final Pattern H2_OPEN_PATTERN = Pattern.compile("<h2[^>]*class=\"[^\"]*chapter-title[^\"]*\"[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern READ_BTN_PATTERN = Pattern.compile("<div[^>]*class=\"[^\"]*read_btn[^\"]*\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_STYLE_PATTERN = Pattern.compile("<p\\s+style=", Pattern.CASE_INSENSITIVE);
    private static final Pattern PHB_FM_PATTERN = Pattern.compile("<div[^>]*class=\"[^\"]*phb_fm", Pattern.CASE_INSENSITIVE);
    private static final Pattern SCRIPT_PATTERN = Pattern.compile("<script\\b[\\s\\S]*?</script>", Pattern.CASE_INSENSITIVE);
    private static final Pattern STYLE_PATTERN = Pattern.compile("<style\\b[\\s\\S]*?</style>", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMMENT_PATTERN = Pattern.compile("<!--[\\s\\S]*?-->");
    private static final Pattern P_BLOCK_PATTERN = Pattern.compile("<p\\b[^>]*>([\\s\\S]*?)</p\\s*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ANCHOR_OPEN_PATTERN = Pattern.compile("<a\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_TITLE_PATTERN = Pattern.compile("<title>([\\s\\S]*?)</title>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ANCHOR_PATTERN = Pattern.compile("<a\\b([^>]*)>([\\s\\S]*?)</a>", Pattern.CASE_INSENSITIVE);
    private static final Pattern HREF_PATTERN = Pattern.compile("href\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))", Pattern.CASE_INSENSITIVE);
    private static final Pattern LASTREAD_PATTERN = Pattern.compile("lastread\\.set\\([^)]*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SINGLE_QUOTED_ARG = Pattern.compile("'([^']*)'");
    private static final Pattern TITLE_PAGE_PATTERN = Pattern.compile("第(\\d+)页");
    private static final Pattern SELECT_PATTERN = Pattern.compile("<select[\\s\\S]*?</select>", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOC_LIST_PATTERN = Pattern.compile("<ul[^>]+class=\"[^\"]*\\b(?:ph_list|section-list)\\b[^\"]*\"[^>]*>([\\s\\S]*?)</ul>", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRAILING_PARENS_PATTERN = Pattern.compile("[(（][^()（）]{1,20}[)）]\\s*$");

    private static final Pattern SEARCH_DESKTOP_LIST = Pattern.compile("<ul[^>]*id=[\"']novel-search[\"'][^>]*>([\\s\\S]*?)</ul>", Pattern.CASE_INSENSITIVE);
    private static final Pattern LI_PATTERN = Pattern.compile("<li\\b[^>]*>([\\s\\S]*?)</li>", Pattern.CASE_INSENSITIVE);
    private static final Pattern SEARCH_ANCHOR_PATTERN = Pattern.compile("<a[^>]*href=[\"']([^\"']+)[\"'][^>]*>([\\s\\S]*?)</a>", Pattern.CASE_INSENSITIVE);
    private static final Pattern SEARCH_HEAD_PATTERN = Pattern.compile("<h2[^>]*>[^<]*搜索结果");
    private static final Pattern SEARCH_REGION_END_PATTERN = Pattern.compile("<div[^>]*class=[\"'][^\"']*\\bindex_qb\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SEARCH_LIST_ANCHOR_PATTERN = Pattern.compile("<a\\b([^>]*class=[\"'][^\"']*\\blist\\b[^\"']*[\"'][^>]*)>([\\s\\S]*?)</a>", Pattern.CASE_INSENSITIVE);
    private static final Pattern SEARCH_HREF_PATTERN = Pattern.compile("href=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern SEARCH_TITLE_ATTR_PATTERN = Pattern.compile("title=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern H4_PATTERN = Pattern.compile("<h4[^>]*>([\\s\\S]*?)</h4>", Pattern.CASE_INSENSITIVE);
    private static final Pattern INFO_DIV_PATTERN = Pattern.compile("<div[^>]*class=[\"']info[\"'][^>]*>([\\s\\S]*?)</div>", Pattern.CASE_INSENSITIVE);
    private static final Pattern BOOK_HREF_PATTERN = Pattern.compile("^(?:https?://(?:www\\.)?wanshuge\\.org)?/book/([1-9]\\d{0,8})/?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTROL_CHARS_PATTERN = Pattern.compile("[\\u0000-\\u001f\\u007f]");
    private static final Pattern WHITESPACE_RUN_PATTERN = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    private static final Pattern INTRO_TAIL_PATTERN = Pattern.compile("三秒记住本站[\\s\\S]*$");
    private static final Pattern INTRO_PROVIDER_PATTERN = Pattern.compile("[^。！？\\n]*万书阁免费提供[^。！？\\n]*[。！？]?");
    private static final Pattern INTRO_SHARE_PATTERN = Pattern.compile("如果您喜欢[^，,]*[，,]别忘记分享给朋友[.。…]*");
    private static final Pattern INTRO_ABOUT_PATTERN = Pattern.compile("^关于[^：:]{1,40}[：:]");

    public static boolean isNoise(String text) {
        if (text == null || text.isEmpty()) {
            return true;
        }
        for (int i = 0; i < NOISE_PATTERNS.length; i++) {
            if (NOISE_PATTERNS[i].matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    public static boolean isRestrictedNotice(String text) {
        return text != null && RESTRICTED_PATTERN.matcher(text).find();
    }

    public static String cleanText(String raw) {
        if (raw == null) {
            return "";
        }
        String unescaped = Parser.unescapeEntities(raw, false);
        String noTags = unescaped
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p\\s*>", "\n")
                .replaceAll("<[^>]+>", "");

        String t = noTags.replace('\u00a0', ' ').replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = t.split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].replaceAll("[ \\t\\u3000]+", " ").trim();
            if (!line.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(line);
            }
        }
        return sb.toString();
    }

    public static String extractTitle(String html) {
        if (html == null) return "";
        Matcher mh2 = H2_TITLE_PATTERN.matcher(html);
        if (mh2.find()) {
            return cleanTitle(cleanText(mh2.group(1)));
        }
        Matcher mt = HTML_TITLE_PATTERN.matcher(html);
        if (mt.find()) {
            String title = cleanText(mt.group(1))
                    .replaceAll("[_\\-|]\\s*万书阁\\s*$", "")
                    .replaceAll("\\s*第\\d+页\\s*$", "");
            // The source writes "chapter title(author)"; only the trailing bracketed part is dropped.
            title = TRAILING_PARENS_PATTERN.matcher(title).replaceFirst("").trim();
            return cleanTitle(title);
        }
        return "";
    }

    private static String cleanTitle(String raw) {
        return raw
                .replaceAll("[（\\(]第\\d+页[）\\)]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public static Integer extractDeclaredPageIndex(String html) {
        if (html == null) return null;
        Matcher mlr = LASTREAD_PATTERN.matcher(html);
        if (mlr.find()) {
            Matcher mArgs = SINGLE_QUOTED_ARG.matcher(mlr.group(0));
            List<String> args = new ArrayList<String>();
            while (mArgs.find()) {
                args.add(mArgs.group(1));
            }
            if (args.size() >= 5) {
                try {
                    return Integer.parseInt(args.get(4));
                } catch (NumberFormatException ignored) {}
            }
        }

        Matcher mh2 = H2_TITLE_PATTERN.matcher(html);
        String mark = mh2.find() ? mh2.group(1) : "";
        if (mark.isEmpty()) {
            Matcher mt = HTML_TITLE_PATTERN.matcher(html);
            if (mt.find()) {
                mark = mt.group(1);
            }
        }
        Matcher mPage = TITLE_PAGE_PATTERN.matcher(mark);
        if (mPage.find()) {
            try {
                return Integer.parseInt(mPage.group(1)) - 1;
            } catch (NumberFormatException ignored) {}
        }

        return null;
    }

    public static List<String> extractEncodedParagraphs(String html, Base64Decoder decoder) {
        List<String> out = new ArrayList<String>();
        if (html == null || decoder == null) return out;
        Matcher m = QSBS_PATTERN.matcher(html);
        while (m.find()) {
            try {
                byte[] bytes = decoder.decode(m.group(1));
                String decoded = new String(bytes, StandardCharsets.UTF_8);
                String text = cleanText(decoded);
                if (!text.isEmpty()) {
                    out.add(text);
                }
            } catch (Exception ignored) {}
        }
        return out;
    }

    public static List<String> extractPlainParagraphs(String html) {
        List<String> out = new ArrayList<String>();
        if (html == null) return out;
        Pattern candidates[] = new Pattern[] {
                Pattern.compile("<div[^>]+id=\"content\"[^>]*>([\\s\\S]*?)</div>", Pattern.CASE_INSENSITIVE),
                Pattern.compile("<div[^>]+class=\"[^\"]*\\bcontent\\b[^\"]*\"[^>]*>([\\s\\S]*?)</div>", Pattern.CASE_INSENSITIVE),
                Pattern.compile("<div[^>]+class=\"[^\"]*\\bread[-_]?content\\b[^\"]*\"[^>]*>([\\s\\S]*?)</div>", Pattern.CASE_INSENSITIVE)
        };
        for (int i = 0; i < candidates.length; i++) {
            Matcher m = candidates[i].matcher(html);
            if (m.find()) {
                String block = m.group(1);
                String[] parts = block.split("(?i)</p\\s*>|<br\\s*/?>");
                for (int j = 0; j < parts.length; j++) {
                    String p = cleanText(parts[j]);
                    if (!p.isEmpty() && !isNoise(p)) {
                        out.add(p);
                    }
                }
                if (!out.isEmpty()) {
                    return out;
                }
            }
        }
        return out;
    }

    private static int indexOfPattern(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.start() : -1;
    }

    /**
     * Some books put the body directly as bare paragraphs after the chapter heading (no encoding and no
     * content container). The range starts after the first button bar following the heading and ends at
     * the next button bar, the site's promotion paragraph or the recommendation block; paragraphs that
     * contain links are recommendations and are dropped.
     */
    public static List<String> extractInlineParagraphs(String html) {
        List<String> out = new ArrayList<String>();
        if (html == null) return out;
        int h2 = indexOfPattern(H2_OPEN_PATTERN, html);
        if (h2 < 0) return out;
        String rest = html.substring(h2);
        int firstBtn = indexOfPattern(READ_BTN_PATTERN, rest);
        if (firstBtn >= 0) {
            int close = rest.indexOf("</div>", firstBtn);
            if (close >= 0) {
                rest = rest.substring(close + 6);
            }
        }
        int end = -1;
        int[] ends = new int[] {
                indexOfPattern(READ_BTN_PATTERN, rest),
                indexOfPattern(P_STYLE_PATTERN, rest),
                indexOfPattern(PHB_FM_PATTERN, rest)
        };
        for (int i = 0; i < ends.length; i++) {
            if (ends[i] >= 0 && (end < 0 || ends[i] < end)) {
                end = ends[i];
            }
        }
        if (end >= 0) {
            rest = rest.substring(0, end);
        }
        rest = SCRIPT_PATTERN.matcher(rest).replaceAll(" ");
        Matcher m = P_BLOCK_PATTERN.matcher(rest);
        while (m.find()) {
            String inner = m.group(1);
            if (ANCHOR_OPEN_PATTERN.matcher(inner).find()) {
                continue;
            }
            String text = cleanText(inner);
            if (!text.isEmpty()) {
                out.add(text);
            }
        }
        return out;
    }

    public static RawChapterPage parseChapterPage(
            String bookId,
            String html,
            String expectedId,
            int pageIndex,
            Base64Decoder decoder
    ) {
        String title = extractTitle(html);
        List<String> rawParagraphs = extractEncodedParagraphs(html, decoder);
        if (rawParagraphs.isEmpty()) {
            rawParagraphs = extractPlainParagraphs(html);
        }
        if (rawParagraphs.isEmpty()) {
            rawParagraphs = extractInlineParagraphs(html);
        }
        boolean restricted = false;
        for (int i = 0; i < rawParagraphs.size(); i++) {
            if (isRestrictedNotice(rawParagraphs.get(i))) {
                restricted = true;
                break;
            }
        }

        List<String> cleaned = new ArrayList<String>();
        for (int i = 0; i < rawParagraphs.size(); i++) {
            String p = rawParagraphs.get(i).trim();
            if (p.isEmpty() || isNoise(p) || p.equals(title)) {
                continue;
            }
            cleaned.add(p);
        }

        Set<Integer> sameChapterPages = new HashSet<Integer>();
        List<String> prevCandidates = new ArrayList<String>();
        List<String> nextCandidates = new ArrayList<String>();
        String otherFirstId = null;

        String strippedHtml = html;
        strippedHtml = SCRIPT_PATTERN.matcher(strippedHtml).replaceAll(" ");
        strippedHtml = STYLE_PATTERN.matcher(strippedHtml).replaceAll(" ");
        strippedHtml = COMMENT_PATTERN.matcher(strippedHtml).replaceAll(" ");

        Matcher am = ANCHOR_PATTERN.matcher(strippedHtml);
        while (am.find()) {
            String attrs = am.group(1);
            String text = cleanText(am.group(2)).replaceAll("\\s+", "");
            Matcher hm = HREF_PATTERN.matcher(attrs);
            if (!hm.find()) continue;
            String href = hm.group(1) != null ? hm.group(1) : (hm.group(2) != null ? hm.group(2) : hm.group(3));
            ChapterLinkResult parsed = SourceUrls.parseChapterLink(bookId, Parser.unescapeEntities(href, true));
            if (parsed == null) continue;

            if (parsed.getId().equals(expectedId)) {
                if (parsed.getPageIndex() != pageIndex) {
                    sameChapterPages.add(parsed.getPageIndex());
                }
                continue;
            }

            if (parsed.getPageIndex() != 0) continue;

            if (text.equals("上一章") || text.equals("上一页")) {
                prevCandidates.add(parsed.getId());
            } else if (text.equals("下一章") || text.equals("下一页")) {
                nextCandidates.add(parsed.getId());
            } else if (otherFirstId == null) {
                otherFirstId = parsed.getId();
            }
        }

        List<Integer> sortedSame = new ArrayList<Integer>(sameChapterPages);
        Collections.sort(sortedSame);
        Integer nextPageIndex = null;
        for (int i = 0; i < sortedSame.size(); i++) {
            if (sortedSame.get(i) > pageIndex) {
                nextPageIndex = sortedSame.get(i);
                break;
            }
        }

        String prevChapterId = !prevCandidates.isEmpty() ? prevCandidates.get(0) : null;
        String nextChapterId = !nextCandidates.isEmpty()
                ? nextCandidates.get(0)
                : (prevCandidates.isEmpty() && nextCandidates.isEmpty() ? otherFirstId : null);

        return new RawChapterPage(
                expectedId,
                pageIndex,
                !title.isEmpty() ? title : "章节 " + expectedId,
                cleaned,
                prevChapterId,
                nextChapterId,
                nextPageIndex,
                sortedSame,
                extractDeclaredPageIndex(html),
                restricted
        );
    }

    public static List<TocItemRaw> parseTocPageHtml(String bookId, String html, int page) {
        List<TocItemRaw> items = new ArrayList<TocItemRaw>();
        if (html == null) return items;

        Matcher ulMatcher = TOC_LIST_PATTERN.matcher(html);
        while (ulMatcher.find()) {
            extractListItemsInto(bookId, ulMatcher.group(1), items);
        }
        if (!items.isEmpty()) {
            return items;
        }

        extractListItemsInto(bookId, html, items);
        return items;
    }

    private static void extractListItemsInto(String bookId, String block, List<TocItemRaw> out) {
        Matcher am = ANCHOR_PATTERN.matcher(block);
        while (am.find()) {
            String attrs = am.group(1);
            Matcher hm = HREF_PATTERN.matcher(attrs);
            if (!hm.find()) continue;
            String href = hm.group(1) != null ? hm.group(1) : (hm.group(2) != null ? hm.group(2) : hm.group(3));
            ChapterLinkResult parsed = SourceUrls.parseChapterLink(bookId, href);
            if (parsed == null || parsed.getPageIndex() != 0) continue;
            String rawTitle = cleanText(am.group(2)).replaceAll("\\s+", " ").trim();
            if (rawTitle.isEmpty()) continue;
            if (rawTitle.matches("^(上一页|下一页|首页|尾页)$")) continue;

            out.add(new TocItemRaw(parsed.getId(), rawTitle));
        }
    }

    /**
     * Total number of TOC pages, or null when this page does not say. Pages 2+ carry a page selector;
     * a selector that only lists the first range (books of at most 100 chapters) means exactly one page.
     */
    public static Integer parseTocTotalPages(String bookId, String html) {
        SourceUrls.assertBookId(bookId);
        if (html == null) return null;
        Matcher sm = SELECT_PATTERN.matcher(html);
        if (sm.find()) {
            String select = sm.group(0);
            Matcher om = Pattern.compile("/book/" + bookId + "/(\\d+)\\.html").matcher(select);
            int max = -1;
            while (om.find()) {
                int p = Integer.parseInt(om.group(1));
                if (p > max) {
                    max = p;
                }
            }
            if (max >= 0) {
                return max + 1;
            }
            if (Pattern.compile("/book/" + bookId + "/[\"']").matcher(select).find()) {
                return 1;
            }
        }

        Pattern linkPattern = Pattern.compile("/book/" + bookId + "/(\\d+)\\.html\"[^>]*>第[\\d\\-]+章");
        Matcher lm = linkPattern.matcher(html);
        if (lm.find()) {
            return Integer.parseInt(lm.group(1)) + 1;
        }

        return null;
    }

    public static boolean isPromo(String title) {
        if (title == null) return false;
        return title.startsWith("新书") || (title.contains("新书") && title.length() <= 12);
    }

    public static ClassifiedToc classifyTocItems(String bookId, List<TocItemRaw> items, int page) {
        SourceUrls.assertBookId(bookId);
        List<TocEntry> candidates = new ArrayList<TocEntry>();
        List<TocEntry> extras = new ArrayList<TocEntry>();

        long orderKey = 0L;
        for (int i = 0; i < items.size(); i++) {
            TocItemRaw item = items.get(i);
            String title = item.getTitle().trim();
            if (title.isEmpty() || isPromo(title)) continue;
            TitleInfo info = TitleNormalizer.normalizeTitle(title);
            TocEntry entry = new TocEntry(
                    bookId,
                    item.getId(),
                    title,
                    orderKey++,
                    info.isExtra(),
                    page
            );
            if (entry.isExtra()) {
                extras.add(entry);
            } else {
                candidates.add(entry);
            }
        }

        List<Integer> numbers = new ArrayList<Integer>();
        for (int i = 0; i < candidates.size(); i++) {
            TitleInfo info = TitleNormalizer.normalizeTitle(candidates.get(i).getTitle());
            if (info.getNumber() != null && info.getNumber() > 0) {
                numbers.add(info.getNumber());
            }
        }

        Integer mid = null;
        if (!numbers.isEmpty()) {
            Collections.sort(numbers);
            mid = numbers.get(numbers.size() / 2);
        }

        List<TocEntry> main = new ArrayList<TocEntry>();
        for (int i = 0; i < candidates.size(); i++) {
            TocEntry entry = candidates.get(i);
            TitleInfo info = TitleNormalizer.normalizeTitle(entry.getTitle());
            if (page >= 2 && mid != null && info.getNumber() != null && info.getNumber() > 0) {
                int n = info.getNumber();
                if (n < mid / 2 || n > mid * 2) {
                    continue; // Skip noise
                }
            }
            main.add(entry);
        }

        return new ClassifiedToc(main, extras);
    }

    /* ------------------------------------------------------------------ */
    /* Book info (detail page og meta)                                     */
    /* ------------------------------------------------------------------ */

    private static String metaContent(String html, String property) {
        String esc = Pattern.quote(property);
        Matcher m = Pattern.compile("<meta[^>]+property=[\"']" + esc + "[\"'][^>]*content=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE).matcher(html);
        String raw = null;
        if (m.find()) {
            raw = m.group(1);
        } else {
            Matcher m2 = Pattern.compile("<meta[^>]+content=[\"']([^\"']*)[\"'][^>]*property=[\"']" + esc + "[\"']", Pattern.CASE_INSENSITIVE).matcher(html);
            if (m2.find()) {
                raw = m2.group(1);
            }
        }
        if (raw == null) return null;
        String v = cleanText(raw).replaceAll("\n+", " ").trim();
        return v.isEmpty() ? null : v;
    }

    /** Strips the site's boilerplate (watermark line, "provided free by" sentence, share prompt) from an intro. */
    public static String cleanIntro(String raw, String title) {
        if (raw == null) return "";
        String t = raw;
        t = INTRO_TAIL_PATTERN.matcher(t).replaceAll("");
        t = INTRO_PROVIDER_PATTERN.matcher(t).replaceAll("");
        t = INTRO_SHARE_PATTERN.matcher(t).replaceAll("");
        t = INTRO_ABOUT_PATTERN.matcher(t).replaceFirst("");
        t = t.trim();
        if (title != null && !title.isEmpty() && t.equals(title)) {
            t = "";
        }
        return t;
    }

    /** Book metadata from the detail page; null when the page has no book name. */
    public static BookInfo parseBookInfo(String bookId, String html) {
        SourceUrls.assertBookId(bookId);
        if (html == null) return null;
        String title = metaContent(html, "og:novel:book_name");
        if (title == null) title = metaContent(html, "og:title");
        if (title == null) return null;
        String author = metaContent(html, "og:novel:author");
        String category = metaContent(html, "og:novel:category");
        String status = metaContent(html, "og:novel:status");
        String latest = metaContent(html, "og:novel:latest_chapter_name");
        String updated = metaContent(html, "og:novel:update_time");
        String desc = metaContent(html, "og:description");
        String readUrl = metaContent(html, "og:novel:read_url");

        String intro = null;
        if (desc != null) {
            String cleaned = cleanIntro(desc, title);
            if (!cleaned.isEmpty()) intro = cleaned;
        }
        String firstChapterId = null;
        if (readUrl != null) {
            ChapterLinkResult link = SourceUrls.parseChapterLink(bookId, readUrl);
            if (link != null && link.getPageIndex() == 0) firstChapterId = link.getId();
        }
        return new BookInfo(bookId, title, author != null ? author : "", category, status, intro, latest, updated, firstChapterId);
    }

    /* ------------------------------------------------------------------ */
    /* Site search                                                          */
    /* ------------------------------------------------------------------ */

    /** Normalized search text, or null when it is empty, too long or has control characters. */
    public static String normalizeSearchQuery(String raw) {
        if (raw == null) return null;
        if (CONTROL_CHARS_PATTERN.matcher(raw).find()) return null;
        String q = WHITESPACE_RUN_PATTERN.matcher(raw).replaceAll(" ").trim();
        int length = q.codePointCount(0, q.length());
        if (length < 1 || length > MAX_SEARCH_QUERY_CHARS) return null;
        return q;
    }

    private static String bookIdFromHref(String href) {
        Matcher m = BOOK_HREF_PATTERN.matcher(href);
        return m.matches() ? m.group(1) : null;
    }

    private static String textOf(String fragment) {
        return cleanText(fragment).replaceAll("\n+", " ").trim();
    }

    private static String spanContent(String li, String cls) {
        Matcher m = Pattern.compile("<span[^>]*class=[\"']" + cls + "[\"'][^>]*>([\\s\\S]*?)</span>", Pattern.CASE_INSENSITIVE).matcher(li);
        return m.find() ? m.group(1) : null;
    }

    private static String nonEmpty(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    /**
     * Parses a search result page in either template (desktop list or mobile cards).
     * Returns null when no result container exists (changed template or blocked), an empty list for "no hits".
     */
    public static List<BookSummary> parseSearchResults(String html) {
        if (html == null) return null;
        List<BookSummary> out = new ArrayList<BookSummary>();
        Set<String> seen = new HashSet<String>();

        Matcher desktop = SEARCH_DESKTOP_LIST.matcher(html);
        if (desktop.find()) {
            Matcher liMatcher = LI_PATTERN.matcher(desktop.group(1));
            while (liMatcher.find() && out.size() < MAX_SEARCH_RESULTS) {
                String li = liMatcher.group(1);
                String s2 = spanContent(li, "s2");
                if (s2 == null) continue;
                Matcher a = SEARCH_ANCHOR_PATTERN.matcher(s2);
                if (!a.find()) continue;
                String id = bookIdFromHref(Parser.unescapeEntities(a.group(1), true));
                String title = textOf(a.group(2));
                if (id == null || title.isEmpty() || seen.contains(id)) continue;
                seen.add(id);
                String s3 = spanContent(li, "s3");
                String s1 = spanContent(li, "s1");
                String s4 = spanContent(li, "s4");
                String s5 = spanContent(li, "s5");
                String author = s3 == null ? null : nonEmpty(textOf(s3));
                String category = s1 == null ? null : nonEmpty(textOf(s1).replaceAll("^\\[|\\]$", ""));
                String latest = s4 == null ? null : nonEmpty(textOf(s4));
                String updated = s5 == null ? null : nonEmpty(textOf(s5));
                out.add(new BookSummary(id, title, author, category, latest, updated, null));
            }
            return out;
        }

        Matcher head = SEARCH_HEAD_PATTERN.matcher(html);
        if (!head.find()) return null;
        String region = html.substring(head.start());
        Matcher end = SEARCH_REGION_END_PATTERN.matcher(region.substring(1));
        if (end.find()) {
            region = region.substring(0, end.start() + 1);
        }
        Matcher am = SEARCH_LIST_ANCHOR_PATTERN.matcher(region);
        while (am.find() && out.size() < MAX_SEARCH_RESULTS) {
            String attrs = am.group(1);
            Matcher hrefMatcher = SEARCH_HREF_PATTERN.matcher(attrs);
            String id = hrefMatcher.find() ? bookIdFromHref(Parser.unescapeEntities(hrefMatcher.group(1), true)) : null;
            if (id == null || seen.contains(id)) continue;
            String body = am.group(2);
            Matcher h4 = H4_PATTERN.matcher(body);
            String title = h4.find() ? textOf(h4.group(1)) : "";
            if (title.isEmpty()) {
                Matcher titleAttr = SEARCH_TITLE_ATTR_PATTERN.matcher(attrs);
                title = titleAttr.find() ? Parser.unescapeEntities(titleAttr.group(1), true).trim() : "";
            }
            if (title.isEmpty()) continue;
            seen.add(id);
            String intro = null;
            Matcher info = INFO_DIV_PATTERN.matcher(body);
            if (info.find()) {
                String cleaned = cleanIntro(textOf(info.group(1)), title);
                if (!cleaned.isEmpty()) intro = cleaned;
            }
            out.add(new BookSummary(id, title, null, null, null, null, intro));
        }
        return out;
    }
}
