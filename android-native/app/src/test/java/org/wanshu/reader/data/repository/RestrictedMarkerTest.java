package org.wanshu.reader.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RestrictedMarkerTest {

    @Test
    public void markerRoundTrips() {
        assertTrue(RestrictedMarker.isRestricted(RestrictedMarker.encode(true)));
        assertFalse(RestrictedMarker.isRestricted(RestrictedMarker.encode(false)));
        assertEquals("[]", RestrictedMarker.encode(false));
    }

    @Test
    public void legacyRowsAreNotRestricted() {
        assertFalse(RestrictedMarker.isRestricted("[]"));
        assertFalse(RestrictedMarker.isRestricted("[1,2]"));
        assertFalse(RestrictedMarker.isRestricted(""));
        assertFalse(RestrictedMarker.isRestricted(null));
    }
}
