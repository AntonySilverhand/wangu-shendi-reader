package org.wanshu.reader.core.queue;

import org.wanshu.reader.core.net.CancellationToken;

/**
 * Keeps upstream searches at least {@link #MIN_GAP_MS} apart (the site rate-limits searching on its own).
 * It owns no thread: a scheduled search action calls {@link #awaitTurn} and sleeps on the scheduler's
 * bounded worker for the remaining time.
 */
public class SearchGate {
    public static final long MIN_GAP_MS = 5000L;
    private static final long SLEEP_SLICE_MS = 100L;

    private final SchedulerClock clock;
    private long nextAllowedStartMs = 0L;

    public SearchGate(SchedulerClock clock) {
        this.clock = clock != null ? clock : new SystemSchedulerClock();
    }

    /**
     * Reserves the next search start slot and returns how long the caller must wait before starting.
     * Every call reserves a slot, so concurrent callers are spaced out one gap apart.
     */
    public synchronized long reserveDelayMs() {
        long now = clock.nowMs();
        long start = Math.max(now, nextAllowedStartMs);
        nextAllowedStartMs = start + MIN_GAP_MS;
        return start - now;
    }

    /**
     * Blocks until this caller's slot has come. Returns false when the token was cancelled meanwhile
     * (the slot stays consumed, which only makes the next search wait a little longer).
     */
    public boolean awaitTurn(CancellationToken token) throws InterruptedException {
        long deadline = clock.nowMs() + reserveDelayMs();
        while (true) {
            if (token != null && token.isCancelled()) {
                return false;
            }
            long remaining = deadline - clock.nowMs();
            if (remaining <= 0L) {
                return true;
            }
            Thread.sleep(Math.min(remaining, SLEEP_SLICE_MS));
        }
    }
}
