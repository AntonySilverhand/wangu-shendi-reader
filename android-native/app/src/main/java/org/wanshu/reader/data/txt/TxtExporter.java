package org.wanshu.reader.data.txt;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.wanshu.reader.data.content.ContentDatabase;
import org.wanshu.reader.data.content.entity.ChapterBlockEntity;
import org.wanshu.reader.data.content.entity.TocEntryEntity;

public class TxtExporter {

    public static int exportBook(String bookId, ContentDatabase db, OutputStream out) throws IOException {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        List<TocEntryEntity> entries = db.tocDao().getEntries(bookId);

        int count = 0;
        for (int i = 0; i < entries.size(); i++) {
            TocEntryEntity entry = entries.get(i);
            List<ChapterBlockEntity> blocks = db.chapterBlockDao().getBlocks(bookId, entry.chapterId);
            if (blocks == null || blocks.isEmpty()) {
                continue;
            }

            writer.write(entry.title);
            writer.write("\n\n");

            for (int b = 0; b < blocks.size(); b++) {
                writer.write(blocks.get(b).content);
                writer.write("\n");
            }
            writer.write("\n");
            count++;
        }

        writer.flush();
        return count;
    }
}
