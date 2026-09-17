package org.wanshu.reader.core.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BookKeyTest {

    @Test
    public void testOnlineBookIdentification() {
        BookKey onlineKey = new BookKey("36780");
        assertTrue(onlineKey.isOnlineBook());
        assertEquals("36780", onlineKey.getId());

        BookKey localKey = new BookKey("local-12345");
        assertFalse(localKey.isOnlineBook());
        assertEquals("local-12345", localKey.getId());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyIdThrows() {
        new BookKey("   ");
    }

    @Test
    public void testEquality() {
        BookKey key1 = new BookKey("36780");
        BookKey key2 = new BookKey("36780");
        assertEquals(key1, key2);
        assertEquals(key1.hashCode(), key2.hashCode());
    }
}
