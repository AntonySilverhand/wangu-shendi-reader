package org.wanshu.reader.core.download;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RetryPolicyTest {

    @Test
    public void testBackoffDelays() {
        assertEquals(5000L, RetryPolicy.getBackoffDelayMillis(0));
        assertEquals(30000L, RetryPolicy.getBackoffDelayMillis(1));
        assertEquals(120000L, RetryPolicy.getBackoffDelayMillis(2));
        assertEquals(600000L, RetryPolicy.getBackoffDelayMillis(3));
        assertEquals(1800000L, RetryPolicy.getBackoffDelayMillis(4));
        assertEquals(-1L, RetryPolicy.getBackoffDelayMillis(5));
        assertEquals(-1L, RetryPolicy.getBackoffDelayMillis(10));
    }
}
