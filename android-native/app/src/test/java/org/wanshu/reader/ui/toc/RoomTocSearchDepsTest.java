package org.wanshu.reader.ui.toc;

import org.junit.Test;
import java.util.Arrays;
import org.wanshu.reader.data.content.entity.TocEntryEntity;
import static org.junit.Assert.*;

public class RoomTocSearchDepsTest {
    @Test public void chapterNumberIsNotConfusedWithZeroBasedOrderKey() {
        TocEntryEntity first = new TocEntryEntity(); first.bookId = "36780"; first.chapterId = "90001"; first.title = "第一百章 重逢"; first.orderKey = 0;
        TocEntryEntity extra = new TocEntryEntity(); extra.bookId = "36780"; extra.chapterId = "90002"; extra.title = "番外 后记"; extra.orderKey = 100;
        RoomTocSearchDeps deps = new RoomTocSearchDeps("36780", Arrays.asList(first, extra), 44, 0);
        assertEquals(1, deps.searchLoaded("100", 100).size());
        assertEquals("90001", deps.searchLoaded("100", 100).get(0).getEntry().getChapterId());
        assertEquals(1, deps.searchLoaded("90002", 100).size());
        assertEquals(0, deps.loadedPageCount());
        assertFalse(deps.isComplete());
        assertNull(deps.nextLoadablePage()); // Explicit loader owns I/O, this adapter cannot fake it.
    }
    @Test public void localTocIsCompleteWithoutRemotePageMarkers() {
        RoomTocSearchDeps deps = new RoomTocSearchDeps("local-book", null, 1, 0);
        assertTrue(deps.isComplete()); assertEquals(0, deps.countLoadable());
    }
}
