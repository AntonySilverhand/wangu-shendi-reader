package org.wanshu.reader.data.repository;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TocPagingTest {

    @Test
    public void unknownTotalProbesTheFirstTwoPages() {
        assertEquals(2, TocPaging.bound(0, 0));
    }

    @Test
    public void knownTotalIsFollowedWithoutAnyClamp() {
        assertEquals(44, TocPaging.bound(44, 0));
        assertEquals(1, TocPaging.bound(1, 0));
        assertEquals(120, TocPaging.bound(120, 0));
        assertEquals(1000, TocPaging.clampDiscovered(5000));
        assertEquals(120, TocPaging.clampDiscovered(120));
        assertEquals(1, TocPaging.clampDiscovered(0));
    }

    @Test
    public void firstPageOnlyLoadStopsAfterPageOne() {
        assertEquals(1, TocPaging.bound(0, 1));
        assertEquals(1, TocPaging.bound(17, 1));
    }

    @Test
    public void aLoneSavedPageRowDoesNotFixTheTotal() {
        assertEquals(0, TocPaging.totalFromRows(0, 1));
        assertEquals(17, TocPaging.totalFromRows(0, 17));
        assertEquals(2, TocPaging.totalFromRows(0, 2));
    }

    @Test
    public void totalAfterSavingAPage() {
        assertEquals(0, TocPaging.totalAfterPersist(0, null, 1));
        assertEquals(2, TocPaging.totalAfterPersist(0, null, 2));
        assertEquals(1, TocPaging.totalAfterPersist(0, Integer.valueOf(1), 2));
        assertEquals(17, TocPaging.totalAfterPersist(0, Integer.valueOf(17), 2));
        assertEquals(5, TocPaging.totalAfterPersist(5, null, 3));
    }

    @Test
    public void pageRowsAreOnlyCreatedUpToTheKnownTotal() {
        assertEquals(1, TocPaging.rowsUpTo(0, 1));
        assertEquals(17, TocPaging.rowsUpTo(17, 2));
        assertEquals(1, TocPaging.rowsUpTo(1, 2));
    }
}
