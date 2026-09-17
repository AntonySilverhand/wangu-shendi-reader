package org.wanshu.reader.core.toc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TocSearchControllerTest {
    private FakeTocStore store;
    private FakeTocSearchDeps deps;
    private CollectingTocSearchEvents events;
    private TocSearchController controller;

    @Before
    public void setUp() {
        store = new FakeTocStore(5, 10);
        deps = new FakeTocSearchDeps(store);
        events = new CollectingTocSearchEvents();
        controller = new TocSearchController(deps, events);
    }

    @After
    public void tearDown() {
        if (controller != null) {
            controller.shutdown();
        }
    }

    @Test
    public void testExhaustedCleanConvergenceNoLoop() throws Exception {
        // Page 1 loaded, page 2 failed, pages 3..5 unvisited
        store.loaded.put(1, store.entriesOf(1));
        store.failed.add(2);

        controller.setQuery("9999");
        Thread.sleep(800);

        List<Integer> pagesLoaded = new ArrayList<Integer>(deps.store.loaded.keySet());
        Collections.sort(pagesLoaded);
        assertEquals(4, pagesLoaded.size());
        assertTrue(pagesLoaded.contains(1));
        assertTrue(pagesLoaded.contains(3));
        assertTrue(pagesLoaded.contains(4));
        assertTrue(pagesLoaded.contains(5));
        assertFalse(pagesLoaded.contains(2)); // Failed page was not requested again

        TocSearchUpdateItem last = events.getLastUpdate();
        assertNotNull(last);
        assertEquals(TocSearchPhaseKind.DONE, last.phase.getKind());
        assertTrue(last.phase.isExhausted());
        assertFalse(last.phase.isComplete());
        assertEquals(1, last.phase.getFailedPages());
        assertEquals(0, last.results.size());

        // Zero loop / no spin
        int loadCount = deps.store.loadLog.size();
        Thread.sleep(400);
        assertEquals(loadCount, deps.store.loadLog.size());
        assertFalse(controller.isBusy());
    }

    @Test
    public void testRapidInputDebounceAndGeneration() throws Exception {
        controller.setQuery("2");
        controller.setQuery("28");
        controller.setQuery("287");
        controller.setQuery("2871");
        Thread.sleep(800);

        List<String> loads = deps.store.loadLog;
        assertTrue(loads.size() <= 8);
        assertFalse(controller.isBusy());
    }

    @Test
    public void testNumericProbeStopsOnHitWithoutFullScan() throws Exception {
        store = new FakeTocStore(44, 100);
        deps = new FakeTocSearchDeps(store);
        controller = new TocSearchController(deps, events);

        // Search for chapter 2871 (~ page 29)
        controller.setQuery("2871");
        Thread.sleep(800);

        // Probe only loads nearby pages (at most 1 + 2*3 = 7 pages)
        assertTrue(deps.store.loadLog.size() <= 7);
        assertTrue(deps.store.loaded.size() < 10);

        TocSearchUpdateItem last = events.getLastUpdate();
        assertNotNull(last);
        assertTrue(last.results.size() > 0);
        assertEquals(2871L, last.results.get(0).getEntry().getOrderKey());
    }

    @Test
    public void testCancelStopsBackgroundTasksAndDiscardsCallbacks() throws Exception {
        store = new FakeTocStore(44, 100);
        deps = new FakeTocSearchDeps(store);
        controller = new TocSearchController(deps, events);

        controller.setQuery("3000");
        Thread.sleep(120);
        controller.cancel();
        Thread.sleep(500);

        int loads = deps.store.loadLog.size();
        Thread.sleep(400);
        assertEquals(loads, deps.store.loadLog.size());

        TocSearchUpdateItem last = events.getLastUpdate();
        assertNotNull(last);
        assertEquals(TocSearchPhaseKind.CANCELLED, last.phase.getKind());
        assertFalse(controller.isBusy());
    }

    @Test
    public void testAlreadyLoadedDataZeroNetwork() throws Exception {
        store.loaded.put(1, store.entriesOf(1));

        controller.searchNow("3");
        Thread.sleep(300);

        // Zero network calls
        assertEquals(0, deps.store.loadLog.size());

        TocSearchUpdateItem last = events.getLastUpdate();
        assertNotNull(last);
        assertTrue(last.results.size() > 0);
        assertEquals(3L, last.results.get(0).getEntry().getOrderKey());
    }

    @Test
    public void testResumeAfterRetry() throws Exception {
        store.failed.add(2);

        controller.setQuery("25");
        Thread.sleep(800);

        TocSearchUpdateItem last = events.getLastUpdate();
        assertNotNull(last);
        assertEquals(TocSearchPhaseKind.DONE, last.phase.getKind());
        assertEquals(1, deps.store.failed.size());

        controller.resumeAfterRetry("25");
        Thread.sleep(800);

        assertEquals(0, deps.store.failed.size());
        TocSearchUpdateItem finalUpdate = events.getLastUpdate();
        assertNotNull(finalUpdate);
        assertTrue(finalUpdate.results.size() > 0);
        assertEquals(25L, finalUpdate.results.get(0).getEntry().getOrderKey());
    }
}
