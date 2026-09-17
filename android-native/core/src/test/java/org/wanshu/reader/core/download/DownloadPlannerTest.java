package org.wanshu.reader.core.download;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;

public class DownloadPlannerTest {

    private List<String> synthetic200Toc;

    @Before
    public void setUp() {
        synthetic200Toc = new ArrayList<String>(200);
        for (int i = 1; i <= 200; i++) {
            synthetic200Toc.add(String.valueOf(i));
        }
    }

    @Test
    public void testEntireBookOrderFromAnchor60() {
        // Anchor is chapter 60 (at index 59)
        List<String> order = DownloadPlanner.buildPlannedChapterOrder(
                synthetic200Toc,
                "60",
                DownloadRange.ENTIRE_BOOK
        );

        assertEquals(200, order.size());

        // Phase 1: next 50 chapters (61 to 110)
        assertEquals("61", order.get(0));
        assertEquals("110", order.get(49));

        // Phase 2: far subsequent chapters to end of TOC (111 to 200)
        assertEquals("111", order.get(50));
        assertEquals("200", order.get(139));

        // Phase 3: previous chapters from anchor backwards to book start (59 down to 1)
        assertEquals("59", order.get(140));
        assertEquals("58", order.get(141));
        assertEquals("1", order.get(198));

        // Anchor itself
        assertEquals("60", order.get(199));
    }

    @Test
    public void testNext50OrderFromAnchor60() {
        List<String> order = DownloadPlanner.buildPlannedChapterOrder(
                synthetic200Toc,
                "60",
                DownloadRange.NEXT_50
        );

        assertEquals(50, order.size());
        assertEquals("61", order.get(0));
        assertEquals("110", order.get(49));
    }

    @Test
    public void testNext200OrderFromAnchor60() {
        List<String> order = DownloadPlanner.buildPlannedChapterOrder(
                synthetic200Toc,
                "60",
                DownloadRange.NEXT_200
        );

        // From 61 to 200 is 140 chapters (limited by total chapters 200)
        assertEquals(140, order.size());
        assertEquals("61", order.get(0));
        assertEquals("200", order.get(139));
    }

    @Test
    public void testGetMissingChaptersSkipsCompleted() {
        List<String> order = DownloadPlanner.buildPlannedChapterOrder(
                synthetic200Toc,
                "60",
                DownloadRange.NEXT_50
        );

        Set<String> completed = new HashSet<String>();
        // Mark 61 to 70 as completed
        for (int i = 61; i <= 70; i++) {
            completed.add(String.valueOf(i));
        }

        List<String> missing = DownloadPlanner.getMissingChapters(order, completed);
        assertEquals(40, missing.size());
        assertEquals("71", missing.get(0));
        assertEquals("110", missing.get(39));
        assertFalse(missing.contains("61"));
        assertFalse(missing.contains("70"));
    }

    @Test
    public void testGetElevatedChapters() {
        Set<String> completed = new HashSet<String>();
        completed.add("61"); // 61 is completed, 60, 62, 63 missing

        List<String> elevated = DownloadPlanner.getElevatedChapters(
                synthetic200Toc,
                "60",
                3,
                completed
        );

        // 60 (current not completed), 62, 63
        assertEquals(3, elevated.size());
        assertEquals("60", elevated.get(0));
        assertEquals("62", elevated.get(1));
        assertEquals("63", elevated.get(2));
    }

    @Test
    public void testConsecutiveOfflineCount() {
        Set<String> completed = new HashSet<String>();
        // Chapters 61, 62, 63 completed, 64 missing
        completed.add("61");
        completed.add("62");
        completed.add("63");

        int count = DownloadPlanner.calculateConsecutiveOfflineCount(
                synthetic200Toc,
                "60",
                completed
        );
        assertEquals(3, count);

        // If 61 is missing, count is 0
        completed.remove("61");
        int countZero = DownloadPlanner.calculateConsecutiveOfflineCount(
                synthetic200Toc,
                "60",
                completed
        );
        assertEquals(0, countZero);
    }

    @Test
    public void testJumpToChapter150Prioritizes151WhilePreservingOldPlan() {
        // Plan was created at anchor 60
        List<String> order = DownloadPlanner.buildPlannedChapterOrder(
                synthetic200Toc,
                "60",
                DownloadRange.ENTIRE_BOOK
        );

        Set<String> completed = new HashSet<String>();
        for (int i = 61; i <= 70; i++) {
            completed.add(String.valueOf(i));
        }

        // User jumps to chapter 150, 151 is uncompleted
        List<String> elevated = DownloadPlanner.getElevatedChapters(
                synthetic200Toc,
                "150",
                3,
                completed
        );

        // 150 and 151, 152, 153 are elevated
        assertTrue(elevated.contains("151"));
        assertEquals("150", elevated.get(0));
        assertEquals("151", elevated.get(1));

        // Original plan order still has anchor 60 and ends with 60
        assertEquals("61", order.get(0));
        assertEquals("60", order.get(199));
    }

    @Test
    public void testCached61To160ResumesAt161WhenReadingAt80() {
        // Plan established with anchor 60
        List<String> order = DownloadPlanner.buildPlannedChapterOrder(
                synthetic200Toc,
                "60",
                DownloadRange.ENTIRE_BOOK
        );

        // Chapters 61 to 160 are already completed in cache
        Set<String> completed = new HashSet<String>();
        for (int i = 61; i <= 160; i++) {
            completed.add(String.valueOf(i));
        }

        // User is currently reading chapter 80 (inside 61-160)
        List<String> missing = DownloadPlanner.getMissingChapters(order, completed);

        // Next missing chapter in sequence MUST be 161!
        assertEquals("161", missing.get(0));
        assertFalse(missing.contains("80"));
        assertFalse(missing.contains("81"));
        assertFalse(missing.contains("160"));
    }
}
