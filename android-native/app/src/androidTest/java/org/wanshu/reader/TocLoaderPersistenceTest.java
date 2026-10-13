package org.wanshu.reader;

import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wanshu.reader.core.queue.RequestScheduler;
import org.wanshu.reader.core.queue.SystemSchedulerClock;
import org.wanshu.reader.data.content.ContentDatabase;
import org.wanshu.reader.data.repository.NativeTocLoader;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class TocLoaderPersistenceTest {
    @Test public void failedPageIsDurableAndRetrySkipsCommittedPages() throws Exception {
        ContentDatabase db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().getTargetContext(), ContentDatabase.class).build();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        SystemSchedulerClock clock = new SystemSchedulerClock();
        RequestScheduler scheduler = new RequestScheduler(clock);
        FakeTocSourceClient client = new FakeTocSourceClient();
        NativeTocLoader loader = new NativeTocLoader(db, scheduler, client, executor);
        Object owner = new Object();
        try {
            TocLoadTestListener first = new TocLoadTestListener(); loader.load("36780", owner, first);
            assertTrue(first.done.await(5, TimeUnit.SECONDS)); assertNotNull(first.error);
            assertEquals(1, first.loaded); assertEquals(2, first.total);
            assertEquals("FAILED", db.tocDao().getTocPage("36780", 2).status);
            assertEquals(1, db.tocDao().getEntriesCount("36780"));
            client.failSecond = false;
            TocLoadTestListener retry = new TocLoadTestListener(); loader.load("36780", owner, retry);
            assertTrue(retry.done.await(5, TimeUnit.SECONDS)); assertNull(retry.error);
            assertEquals(2, retry.loaded); assertEquals(3, client.calls.get());
            assertEquals(2, db.tocDao().getEntriesCount("36780"));
            TocLoadTestListener cached = new TocLoadTestListener(); loader.load("36780", owner, cached);
            assertTrue(cached.done.await(5, TimeUnit.SECONDS)); assertNull(cached.error);
            assertEquals(3, client.calls.get()); // A fully committed TOC requires zero requests.
            try { loader.load("local-book", owner, new TocLoadTestListener()); fail("local TXT must never use source loader"); }
            catch (IllegalArgumentException expected) { assertEquals(3, client.calls.get()); }
        } finally {
            loader.cancel(owner); scheduler.shutdown(); clock.shutdown();
            executor.shutdown(); assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS)); db.close();
        }
    }
}
