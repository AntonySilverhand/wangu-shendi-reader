package org.wanshu.reader.core.txt;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class TxtSplitter {

    public static final Pattern HEADING_PATTERN = Pattern.compile(
            "^[ \\t　]*(?:(?:番外|外传)?第[零〇一二三四五六七八九十百千万两\\d]+[章节回卷][^\\n]{0,60}|(?:序章|序言|楔子|尾声|后记|完本感言|番外|致读者)[^。！？!?\\n]{0,14}|Chapter\\s+\\d+[^\\n]{0,60}|[0-9]{1,4}[、.．][^\\n]{0,60})[ \\t　]*$",
            Pattern.CASE_INSENSITIVE
    );

    public static List<RawTxtChapter> split(String text) {
        ListTxtChapterConsumer consumer = new ListTxtChapterConsumer();
        if (text == null || text.trim().isEmpty()) {
            return consumer.getChapters();
        }
        try {
            split(new BufferedReader(new StringReader(text)), consumer);
        } catch (IOException ignored) {}
        return consumer.getChapters();
    }

    public static void split(BufferedReader reader, TxtChapterConsumer consumer) throws IOException {
        if (reader == null || consumer == null) return;

        String currentTitle = null;
        List<String> currentLines = new ArrayList<String>();
        int chapterIndex = 0;
        String line;

        while ((line = reader.readLine()) != null) {
            String trimmed = line.trim();
            if (HEADING_PATTERN.matcher(trimmed).matches()) {
                // Emit previous chapter if exists
                if (currentTitle != null) {
                    consumer.onChapter(new RawTxtChapter(currentTitle, currentLines), chapterIndex++);
                    currentLines.clear();
                }
                currentTitle = trimmed;
                continue;
            }

            if (currentTitle == null) {
                if (!trimmed.isEmpty()) {
                    currentTitle = "正文";
                    currentLines.add(trimmed);
                }
                continue;
            }

            if (!trimmed.isEmpty()) {
                currentLines.add(trimmed);
            }
        }

        // Emit final chapter
        if (currentTitle != null) {
            consumer.onChapter(new RawTxtChapter(currentTitle, currentLines), chapterIndex);
        }
    }
}
