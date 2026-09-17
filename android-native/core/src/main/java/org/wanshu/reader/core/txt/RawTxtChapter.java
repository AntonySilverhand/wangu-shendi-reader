package org.wanshu.reader.core.txt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RawTxtChapter {
    private final String title;
    private final List<String> paragraphs;
    private final int charCount;

    public RawTxtChapter(String title, List<String> paragraphs) {
        this.title = title != null ? title.trim() : "正文";
        if (paragraphs != null) {
            this.paragraphs = Collections.unmodifiableList(new ArrayList<String>(paragraphs));
        } else {
            this.paragraphs = Collections.<String>emptyList();
        }
        int count = 0;
        for (int i = 0; i < this.paragraphs.size(); i++) {
            count += this.paragraphs.get(i).length();
        }
        this.charCount = count;
    }

    public String getTitle() {
        return title;
    }

    public List<String> getParagraphs() {
        return paragraphs;
    }

    public int getCharCount() {
        return charCount;
    }
}
