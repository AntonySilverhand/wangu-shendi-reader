package org.wanshu.reader.core.source;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.wanshu.reader.core.model.ChapterResult;
import org.wanshu.reader.core.model.TocEntry;
import org.wanshu.reader.core.text.TextBlock;
import org.wanshu.reader.core.text.TextBlockBuilder;
import org.wanshu.reader.core.util.JavaBase64Decoder;

public class SourceContractDumper {

    public static void main(String[] args) throws Exception {
        String outputPath;
        if (args.length > 0) {
            outputPath = args[0];
        } else {
            outputPath = "../contracts/native/source-contracts-native.json";
        }

        File outFile = new File(outputPath);
        File parent = outFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8));
        out.println("{");

        // 1. Single pages
        out.println("  \"singlePages\": [");
        dumpSinglePages(out);
        out.println("  ],");

        // 2. Assembled chapters
        out.println("  \"assembledChapters\": [");
        dumpAssembledChapters(out);
        out.println("  ],");

        // 3. TOC
        out.println("  \"toc\": {");
        dumpToc(out);
        out.println("  },");

        // 4. Synthetic
        out.println("  \"synthetic\": {");
        dumpSynthetic(out);
        out.println("  }");

        out.println("}");
        out.flush();
        out.close();

        System.out.println("SourceContractDumper successfully wrote: " + outFile.getAbsolutePath());
    }

    private static void dumpSinglePages(PrintWriter out) {
        String[] files = new String[] {
                "chapter-13375259-p1.html",
                "chapter-38621328-p1.html",
                "chapter-38621328-p2.html",
                "chapter-38621328-p3.html",
                "chapter-38621330-p2.html",
                "chapter-38621571-loopback.html",
                "chapter-38621571-p1.html",
                "chapter-38621571-p2.html",
                "chapter-38621571-p3.html",
                "chapter-38626103-p1.html",
                "chapter-38626103-p2.html",
                "chapter-38626103-p3.html",
                "chapter-38626103-p4.html",
                "chapter-38626103-p5.html",
                "chapter-8924760-p1.html"
        };
        Arrays.sort(files);

        Pattern pattern = Pattern.compile("chapter-(\\d+)-(?:p(\\d+)|loopback)\\.html");

        for (int i = 0; i < files.length; i++) {
            String f = files[i];
            Matcher m = pattern.matcher(f);
            if (!m.matches()) continue;
            String id = m.group(1);
            int pageIndex;
            if (f.contains("loopback")) {
                pageIndex = 3;
            } else {
                pageIndex = Integer.parseInt(m.group(2)) - 1;
            }

            String html = FixtureHelper.readFixture(f);
            RawChapterPage parsed = SourceParser.parseChapterPage(html, id, pageIndex, new JavaBase64Decoder());

            out.println("    {");
            out.println("      \"file\": \"" + escapeJson(f) + "\",");
            out.println("      \"id\": \"" + escapeJson(id) + "\",");
            out.println("      \"pageIndex\": " + pageIndex + ",");
            out.println("      \"title\": " + quoteOrNull(parsed.getTitle()) + ",");
            out.println("      \"declaredPageIndex\": " + parsed.getDeclaredPageIndex() + ",");
            out.println("      \"prevChapterId\": " + quoteOrNull(parsed.getPrevChapterId()) + ",");
            out.println("      \"nextChapterId\": " + quoteOrNull(parsed.getNextChapterId()) + ",");
            out.print("      \"sameChapterPages\": [");
            List<Integer> same = parsed.getSameChapterPages();
            for (int s = 0; s < same.size(); s++) {
                out.print(same.get(s));
                if (s + 1 < same.size()) out.print(", ");
            }
            out.println("],");
            out.println("      \"paragraphsCount\": " + parsed.getParagraphs().size() + ",");
            out.println("      \"paragraphs\": [");
            dumpStringList(out, parsed.getParagraphs(), "        ");
            out.println("      ]");
            out.print("    }");
            if (i + 1 < files.length) {
                out.println(",");
            } else {
                out.println();
            }
        }
    }

    private static void dumpAssembledChapters(PrintWriter out) {
        String[] cases = new String[] {
                "chapter-13375259-fallback",
                "chapter-8924760-single",
                "chapter-38621328-3pages",
                "chapter-38621571-loopback",
                "chapter-38626103-5pages"
        };

        for (int i = 0; i < cases.length; i++) {
            String c = cases[i];
            MockPageFetcher fetcher = new MockPageFetcher();
            String id;
            if (c.equals("chapter-13375259-fallback")) {
                id = "13375259";
                fetcher.addPage(0, FixtureHelper.readFixture("chapter-13375259-p1.html"));
            } else if (c.equals("chapter-8924760-single")) {
                id = "8924760";
                fetcher.addPage(0, FixtureHelper.readFixture("chapter-8924760-p1.html"));
            } else if (c.equals("chapter-38621328-3pages")) {
                id = "38621328";
                fetcher.addPage(0, FixtureHelper.readFixture("chapter-38621328-p1.html"));
                fetcher.addPage(1, FixtureHelper.readFixture("chapter-38621328-p2.html"));
                fetcher.addPage(2, FixtureHelper.readFixture("chapter-38621328-p3.html"));
            } else if (c.equals("chapter-38621571-loopback")) {
                id = "38621571";
                fetcher.addPage(0, FixtureHelper.readFixture("chapter-38621571-p1.html"));
                fetcher.addPage(1, FixtureHelper.readFixture("chapter-38621571-p2.html"));
                fetcher.addPage(2, FixtureHelper.readFixture("chapter-38621571-p3.html"));
                fetcher.addPage(3, FixtureHelper.readFixture("chapter-38621571-loopback.html"));
            } else {
                id = "38626103";
                fetcher.addPage(0, FixtureHelper.readFixture("chapter-38626103-p1.html"));
                fetcher.addPage(1, FixtureHelper.readFixture("chapter-38626103-p2.html"));
                fetcher.addPage(2, FixtureHelper.readFixture("chapter-38626103-p3.html"));
                fetcher.addPage(3, FixtureHelper.readFixture("chapter-38626103-p4.html"));
                fetcher.addPage(4, FixtureHelper.readFixture("chapter-38626103-p5.html"));
            }

            ChapterAssembler assembler = new ChapterAssembler(fetcher);
            ChapterResult res = assembler.assemble("36780", id);

            out.println("    {");
            out.println("      \"caseName\": \"" + escapeJson(c) + "\",");
            out.println("      \"result\": {");
            out.println("        \"id\": \"" + escapeJson(res.getChapterId()) + "\",");
            out.println("        \"title\": " + quoteOrNull(res.getTitle()) + ",");
            out.println("        \"paragraphs\": [");
            dumpStringList(out, res.getParagraphs(), "          ");
            out.println("        ],");
            out.println("        \"prevId\": " + quoteOrNull(res.getPrevChapterId()) + ",");
            out.println("        \"nextId\": " + quoteOrNull(res.getNextChapterId()) + ",");
            out.println("        \"pageCount\": " + res.getPageCount() + ",");
            out.println("        \"charCount\": " + res.getCharCount() + ",");
            out.println("        \"complete\": " + res.isComplete() + ",");
            out.print("        \"missingPages\": [");
            List<Integer> missing = res.getMissingPages();
            for (int m = 0; m < missing.size(); m++) {
                out.print(missing.get(m));
                if (m + 1 < missing.size()) out.print(", ");
            }
            out.println("]");
            out.println("      }");
            out.print("    }");
            if (i + 1 < cases.length) {
                out.println(",");
            } else {
                out.println();
            }
        }
    }

    private static void dumpToc(PrintWriter out) {
        String html1 = FixtureHelper.readFixture("toc-page1.html");
        String html2 = FixtureHelper.readFixture("toc-page2.html");

        List<TocItemRaw> raw1 = SourceParser.parseTocPageHtml(html1, 1);
        List<TocItemRaw> raw2 = SourceParser.parseTocPageHtml(html2, 2);

        ClassifiedToc c1 = SourceParser.classifyTocItems(raw1, 1);
        ClassifiedToc c2 = SourceParser.classifyTocItems(raw2, 2);

        List<TocEntry> p1Combined = new ArrayList<TocEntry>(c1.getMain().size() + c1.getExtras().size());
        p1Combined.addAll(c1.getMain());
        p1Combined.addAll(c1.getExtras());

        List<TocEntry> p2Combined = new ArrayList<TocEntry>(c2.getMain().size() + c2.getExtras().size());
        p2Combined.addAll(c2.getMain());
        p2Combined.addAll(c2.getExtras());

        List<List<TocEntry>> pages = new ArrayList<List<TocEntry>>();
        pages.add(p1Combined);
        pages.add(p2Combined);

        List<TocEntry> merged = TocMerger.mergeTocPages(pages);

        out.println("    \"page1\": {");
        out.println("      \"rawCount\": " + raw1.size() + ",");
        out.println("      \"classifiedCount\": " + (c1.getMain().size() + c1.getExtras().size()) + ",");
        out.println("      \"totalPagesParsed\": " + SourceParser.parseTocTotalPages(html1));
        out.println("    },");

        out.println("    \"page2\": {");
        out.println("      \"rawCount\": " + raw2.size() + ",");
        out.println("      \"classifiedCount\": " + (c2.getMain().size() + c2.getExtras().size()) + ",");
        out.println("      \"totalPagesParsed\": " + SourceParser.parseTocTotalPages(html2));
        out.println("    },");

        out.println("    \"merged\": {");
        out.println("      \"totalEntries\": " + merged.size() + ",");
        if (!merged.isEmpty()) {
            TocEntry first = merged.get(0);
            TitleInfo firstInfo = TitleNormalizer.normalizeTitle(first.getTitle());
            out.println("      \"firstEntry\": {");
            out.println("        \"id\": \"" + escapeJson(first.getChapterId()) + "\",");
            out.println("        \"title\": \"" + escapeJson(first.getTitle()) + "\",");
            out.println("        \"displayTitle\": \"" + escapeJson(firstInfo.getDisplayTitle()) + "\",");
            out.println("        \"number\": " + firstInfo.getNumber() + ",");
            out.println("        \"extra\": " + first.isExtra());
            out.println("      },");

            TocEntry last = merged.get(merged.size() - 1);
            TitleInfo lastInfo = TitleNormalizer.normalizeTitle(last.getTitle());
            out.println("      \"lastEntry\": {");
            out.println("        \"id\": \"" + escapeJson(last.getChapterId()) + "\",");
            out.println("        \"title\": \"" + escapeJson(last.getTitle()) + "\",");
            out.println("        \"displayTitle\": \"" + escapeJson(lastInfo.getDisplayTitle()) + "\",");
            out.println("        \"number\": " + lastInfo.getNumber() + ",");
            out.println("        \"extra\": " + last.isExtra());
            out.println("      },");

            out.println("      \"entriesSample\": [");
            int sampleCount = Math.min(10, merged.size());
            for (int s = 0; s < sampleCount; s++) {
                TocEntry e = merged.get(s);
                TitleInfo info = TitleNormalizer.normalizeTitle(e.getTitle());
                out.println("        {");
                out.println("          \"id\": \"" + escapeJson(e.getChapterId()) + "\",");
                out.println("          \"title\": \"" + escapeJson(e.getTitle()) + "\",");
                out.println("          \"displayTitle\": \"" + escapeJson(info.getDisplayTitle()) + "\",");
                out.println("          \"number\": " + info.getNumber() + ",");
                out.println("          \"extra\": " + e.isExtra());
                out.print("        }");
                if (s + 1 < sampleCount) {
                    out.println(",");
                } else {
                    out.println();
                }
            }
            out.println("      ]");
        } else {
            out.println("      \"entriesSample\": []");
        }
        out.println("    }");
    }

    private static void dumpSynthetic(PrintWriter out) {
        // Case 1: intra-page duplicate
        RawChapterPage p1_0 = new RawChapterPage("90001", 0, "90001",
                Arrays.asList(
                        "第一段：两军对垒，气氛凝重。",
                        "“杀！”",
                        "“杀！”",
                        "“杀！”",
                        "后退者死，唯有向前。"
                ), null, null, 1, Arrays.asList(0, 1), 0);
        RawChapterPage p1_1 = new RawChapterPage("90001", 1, "90001",
                Arrays.asList(
                        "后退者死，唯有向前。",
                        "战鼓轰鸣，天地变色。"
                ), null, null, null, Arrays.asList(0, 1), 1);
        SyntheticPageFetcher f1 = new SyntheticPageFetcher(Arrays.asList(p1_0, p1_1));
        ChapterAssembler a1 = new ChapterAssembler(f1);
        ChapterResult r1 = a1.assemble("36780", "90001");

        out.println("    \"intraPageLegitimateDuplicate\": {");
        out.println("      \"nativeOutput\": [");
        dumpStringList(out, r1.getParagraphs(), "        ");
        out.println("      ]");
        out.println("    },");

        // Case 2: non-adjacent missing page
        RawChapterPage p2_0 = new RawChapterPage("90002", 0, "90002",
                Arrays.asList(
                        "天地玄黄，宇宙洪荒。",
                        "日月盈昃，辰宿列张。"
                ), null, null, 1, Arrays.asList(0, 1, 2), 0);
        RawChapterPage p2_2 = new RawChapterPage("90002", 2, "90002",
                Arrays.asList(
                        "日月盈昃，辰宿列张。",
                        "寒来暑往，秋收冬藏。"
                ), null, null, null, Arrays.asList(0, 1, 2), 2);
        SyntheticPageFetcher f2 = new SyntheticPageFetcher(Arrays.asList(p2_0, p2_2));
        f2.addFailure(1);
        ChapterAssembler a2 = new ChapterAssembler(f2);
        ChapterResult r2 = a2.assemble("36780", "90002");

        out.println("    \"nonAdjacentMissingPageRepetition\": {");
        out.println("      \"nativeOutput\": [");
        dumpStringList(out, r2.getParagraphs(), "        ");
        out.println("      ],");
        out.println("      \"complete\": " + r2.isComplete() + ",");
        out.print("      \"missingPages\": [");
        List<Integer> m2 = r2.getMissingPages();
        for (int m = 0; m < m2.size(); m++) {
            out.print(m2.get(m));
            if (m + 1 < m2.size()) out.print(", ");
        }
        out.println("]");
        out.println("    },");

        // Case 3: long paragraph and emoji
        StringBuilder sb = new StringBuilder();
        sb.append("万古神帝正文长段开始：");
        for (int i = 0; i < 600; i++) {
            sb.append("天地初开，大道争锋。⚡");
        }
        sb.append("🐉📖⚡𠮷");
        String longText = sb.toString();

        TextBlockBuilder builder = new TextBlockBuilder(4000);
        List<TextBlock> blocks = builder.buildBlocks(Arrays.asList(longText));
        out.println("    \"longParagraphAndEmoji\": {");
        out.println("      \"totalChars\": " + longText.length() + ",");
        out.println("      \"blocksCount\": " + blocks.size() + ",");
        out.print("      \"blockCharLengths\": [");
        for (int b = 0; b < blocks.size(); b++) {
            out.print(blocks.get(b).getText().length());
            if (b + 1 < blocks.size()) out.print(", ");
        }
        out.println("]");
        out.println("    }");
    }

    private static void dumpStringList(PrintWriter out, List<String> list, String indent) {
        for (int i = 0; i < list.size(); i++) {
            out.print(indent + "\"" + escapeJson(list.get(i)) + "\"");
            if (i + 1 < list.size()) {
                out.println(",");
            } else {
                out.println();
            }
        }
    }

    private static String quoteOrNull(String s) {
        if (s == null) return "null";
        return "\"" + escapeJson(s) + "\"";
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < ' ') {
                        String hex = Integer.toHexString(c);
                        sb.append("\\u0000".substring(0, 6 - hex.length())).append(hex);
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        return sb.toString();
    }
}
