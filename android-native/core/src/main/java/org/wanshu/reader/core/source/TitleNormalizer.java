package org.wanshu.reader.core.source;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TitleNormalizer {
    private static final Pattern PREFIX_PATTERN = Pattern.compile("^\\d{1,4}[\\s.、，]+(?=第)");
    private static final Pattern SECTION_PATTERN = Pattern.compile("^分节阅读第(\\d+)节$");
    private static final Pattern CHAPTER_PATTERN = Pattern.compile("^第([零〇一二三四五六七八九十百千万两]+|\\d+)[章回掌][\\s　]*(.*)$");
    private static final Pattern CHAPTER_SPACE_PATTERN = Pattern.compile("^第([零〇一二三四五六七八九十百千万两]+)[\\s　]+(\\S.*)$");
    private static final Pattern EXTRA_PATTERN = Pattern.compile("^番外第([零〇一二三四五六七八九十百千万两\\d]+)章");

    public static TitleInfo normalizeTitle(String title) {
        if (title == null) {
            return new TitleInfo("", null, false);
        }
        String t = title.replaceAll("\\s+", " ").trim();
        t = PREFIX_PATTERN.matcher(t).replaceFirst("");

        Matcher mSection = SECTION_PATTERN.matcher(t);
        if (mSection.matches()) {
            int n = Integer.parseInt(mSection.group(1));
            return new TitleInfo("第" + n + "章", n, false);
        }

        Matcher mChapter = CHAPTER_PATTERN.matcher(t);
        if (mChapter.matches()) {
            Integer n = ChineseNumberParser.parse(mChapter.group(1));
            return new TitleInfo(t, n, false);
        }

        Matcher mSpace = CHAPTER_SPACE_PATTERN.matcher(t);
        if (mSpace.matches()) {
            Integer n = ChineseNumberParser.parse(mSpace.group(1));
            if (n != null && n > 0) {
                return new TitleInfo(t, n, false);
            }
        }

        if (t.startsWith("番外")) {
            Matcher mExtra = EXTRA_PATTERN.matcher(t);
            Integer n = null;
            if (mExtra.find()) {
                n = ChineseNumberParser.parse(mExtra.group(1));
            }
            return new TitleInfo(t, n, true);
        }

        return new TitleInfo(t, null, false);
    }
}
