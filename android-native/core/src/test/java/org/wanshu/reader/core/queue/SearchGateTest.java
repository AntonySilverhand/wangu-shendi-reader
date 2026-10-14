package org.wanshu.reader.core.queue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.wanshu.reader.core.net.CancellationToken;

public class SearchGateTest {

    @Test
    public void firstSearchStartsImmediatelyAndNextOnesAreSpacedByTheGap() {
        FakeSchedulerClock clock = new FakeSchedulerClock();
        SearchGate gate = new SearchGate(clock);
        assertEquals(0L, gate.reserveDelayMs());
        assertEquals(SearchGate.MIN_GAP_MS, gate.reserveDelayMs());
        assertEquals(2 * SearchGate.MIN_GAP_MS, gate.reserveDelayMs());
    }

    @Test
    public void elapsedTimeShortensTheWait() {
        FakeSchedulerClock clock = new FakeSchedulerClock();
        SearchGate gate = new SearchGate(clock);
        assertEquals(0L, gate.reserveDelayMs());
        clock.advanceTime(3000L);
        assertEquals(2000L, gate.reserveDelayMs());
        clock.advanceTime(60000L);
        assertEquals(0L, gate.reserveDelayMs());
    }

    @Test
    public void gapIsAtLeastFiveSeconds() {
        assertTrue(SearchGate.MIN_GAP_MS >= 5000L);
    }

    @Test
    public void awaitTurnReturnsFalseWhenCancelledWhileWaiting() throws Exception {
        FakeSchedulerClock clock = new FakeSchedulerClock();
        SearchGate gate = new SearchGate(clock);
        assertTrue(gate.awaitTurn(null));
        CancellationToken token = new CancellationToken();
        token.cancel();
        assertFalse(gate.awaitTurn(token));
    }
}
