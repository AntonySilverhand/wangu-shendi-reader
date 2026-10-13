package org.wanshu.reader;

import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wanshu.reader.core.model.ChapterResult;
import org.wanshu.reader.core.text.TextBlockBuilder;
import org.wanshu.reader.data.content.ContentDatabase;
import org.wanshu.reader.data.repository.ContentRepository;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SplitParagraphCacheTest {
    @Test public void cachedLongParagraphKeepsItsIdentityAndFullText() throws Exception {
        ContentDatabase db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().getTargetContext(), ContentDatabase.class).build();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        ContentRepository repository = new ContentRepository(db, executor, new TextBlockBuilder(100));
        try {
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < 1000; i++) text.append("长段落😀");
            ChapterResult chapter = new ChapterResult("local-roundtrip", "1", "测试", Arrays.asList("开头", text.toString(), "结尾"),
                    null, null, 1, text.length() + 4, true, Collections.<Integer>emptyList());
            CountDownLatch saved = new CountDownLatch(1);
            repository.saveChapter(chapter, "LOCAL", new ContentTestCompletionCallback(saved));
            assertTrue(saved.await(5, TimeUnit.SECONDS));
            CountDownLatch read = new CountDownLatch(1);
            ReaderTestDataCallback result = new ReaderTestDataCallback(read);
            repository.getChapter("local-roundtrip", "1", result);
            assertTrue(read.await(5, TimeUnit.SECONDS));
            assertNull(result.getError()); assertNotNull(result.getResult());
            assertEquals(chapter.getParagraphs(), result.getResult().getParagraphs());
            assertTrue(result.getResult().isComplete());
        } finally {
            executor.shutdown(); assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS)); db.close();
        }
    }
}
