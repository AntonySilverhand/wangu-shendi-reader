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
import org.wanshu.reader.core.model.TocEntry;
import org.wanshu.reader.core.util.Base64Decoder;

public class SourceParser {

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
            Pattern.compile("^阅读记录$")
    };

    private static final Pattern QSBS_PATTERN = Pattern.compile("qsbs\\.bb\\('([^']*)'\\)");
    private static final Pattern H2_TITLE_PATTERN = Pattern.compile("<h2[^>]*class=\"[^\"]*chapter-title[^\"]*\"[^>]*>([\\s\\S]*?)</h2>", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_TITLE_PATTERN = Pattern.compile("<title>([\\s\\S]*?)</title>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ANCHOR_PATTERN = Pattern.compile("<a\\b([^>]*)>([\\s\\S]*?)</a>", Pattern.CASE_INSENSITIVE);
    private static final Pattern HREF_PATTERN = Pattern.compile("href\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))", Pattern.CASE_INSENSITIVE);
    private static final Pattern LASTREAD_PATTERN = Pattern.compile("lastread\\.set\\([^)]*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SINGLE_QUOTED_ARG = Pattern.compile("'([^']*)'");
    private static final Pattern TITLE_PAGE_PATTERN = Pattern.compile("第(\\d+)页");

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
                    .replaceAll("\\s*第\\d+页\\s*$", "")
                    .replace("(飞天鱼)", "")
                    .trim();
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

    public static RawChapterPage parseChapterPage(
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

        String strippedHtml = html
                .replaceAll("(?i)<script\\b[\\s\\S]*?</script>", " ")
                .replaceAll("(?i)<style\\b[\\s\\S]*?</style>", " ")
                .replaceAll("<!--[\\s\\S]*?-->", " ");

        Matcher am = ANCHOR_PATTERN.matcher(strippedHtml);
        while (am.find()) {
            String attrs = am.group(1);
            String text = cleanText(am.group(2)).replaceAll("\\s+", "");
            Matcher hm = HREF_PATTERN.matcher(attrs);
            if (!hm.find()) continue;
            String href = hm.group(1) != null ? hm.group(1) : (hm.group(2) != null ? hm.group(2) : hm.group(3));
            ChapterLinkResult parsed = SourceUrls.parseChapterLink(href);
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
                extractDeclaredPageIndex(html)
        );
    }

    public static List<TocItemRaw> parseTocPageHtml(String html, int page) {
        List<TocItemRaw> items = new ArrayList<TocItemRaw>();
        if (html == null) return items;

        Pattern ulPattern = Pattern.compile("<ul[^>]+class=\"[^\"]*\\b(?:ph_list|section-list)\\b[^\"]*\"[^>]*>([\\s\\S]*?)</ul>", Pattern.CASE_INSENSITIVE);
        Matcher ulMatcher = ulPattern.matcher(html);
        while (ulMatcher.find()) {
            extractListItemsInto(ulMatcher.group(1), items);
        }
        if (!items.isEmpty()) {
            return items;
        }

        extractListItemsInto(html, items);
        return items;
    }

    private static void extractListItemsInto(String block, List<TocItemRaw> out) {
        Matcher am = ANCHOR_PATTERN.matcher(block);
        while (am.find()) {
            String attrs = am.group(1);
            Matcher hm = HREF_PATTERN.matcher(attrs);
            if (!hm.find()) continue;
            String href = hm.group(1) != null ? hm.group(1) : (hm.group(2) != null ? hm.group(2) : hm.group(3));
            ChapterLinkResult parsed = SourceUrls.parseChapterLink(href);
            if (parsed == null || parsed.getPageIndex() != 0) continue;
            String rawTitle = cleanText(am.group(2)).replaceAll("\\s+", " ").trim();
            if (rawTitle.isEmpty()) continue;
            if (rawTitle.matches("^(上一页|下一页|首页|尾页)$")) continue;

            out.add(new TocItemRaw(parsed.getId(), rawTitle));
        }
    }

    public static Integer parseTocTotalPages(String html) {
        if (html == null) return null;
        Pattern selectPattern = Pattern.compile("<select[\\s\\S]*?</select>", Pattern.CASE_INSENSITIVE);
        Matcher sm = selectPattern.matcher(html);
        if (sm.find()) {
            Matcher om = Pattern.compile("/book/" + SourceUrls.BOOK_ID + "/(\\d+)\\.html").matcher(sm.group(0));
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
        }

        Pattern linkPattern = Pattern.compile("/book/" + SourceUrls.BOOK_ID + "/(\\d+)\\.html\"[^>]*>第[\\d\\-]+章");
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

    public static ClassifiedToc classifyTocItems(List<TocItemRaw> items, int page) {
        List<TocEntry> candidates = new ArrayList<TocEntry>();
        List<TocEntry> extras = new ArrayList<TocEntry>();

        long orderKey = 0L;
        for (int i = 0; i < items.size(); i++) {
            TocItemRaw item = items.get(i);
            String title = item.getTitle().trim();
            if (title.isEmpty() || isPromo(title)) continue;
            TitleInfo info = TitleNormalizer.normalizeTitle(title);
            TocEntry entry = new TocEntry(
                    SourceUrls.BOOK_ID,
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
}
