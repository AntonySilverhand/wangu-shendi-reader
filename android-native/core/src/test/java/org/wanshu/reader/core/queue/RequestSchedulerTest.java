package org.wanshu.reader.core.queue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RequestSchedulerTest {

    @Test
    public void testTotalConcurrencyLimit() {
        FakeSchedulerClock clock = new FakeSchedulerClock();
        QueuedTestExecutor executor = new QueuedTestExecutor();
        RequestScheduler scheduler = new RequestScheduler(clock, executor);

        TestSchedulerAction a1 = new TestSchedulerAction("res1");
        TestSchedulerAction a2 = new TestSchedulerAction("res2");
        TestSchedulerAction a3 = new TestSchedulerAction("res3");

        TestSchedulerCallback cb1 = new TestSchedulerCallback();
        TestSchedulerCallback cb2 = new TestSchedulerCallback();
        TestSchedulerCallback cb3 = new TestSchedulerCallback();

        // Enqueue 3 high priority tasks
        scheduler.enqueue(new RequestKey("k1"), RequestPriority.HIGH, "sub1", a1, cb1);
        clock.advanceTime(200);
        scheduler.enqueue(new RequestKey("k2"), RequestPriority.HIGH, "sub2", a2, cb2);
        clock.advanceTime(200);
        scheduler.enqueue(new RequestKey("k3"), RequestPriority.HIGH, "sub3", a3, cb3);

        // Max concurrency is 2: 2 in flight, 1 pending
        assertEquals(2, scheduler.getInFlightCount());
        assertEquals(1, scheduler.getPendingCount());

        // Run 1 background thread
        executor.runNext();
        assertEquals("res1", cb1.getResult());

        // Now slot freed, advance time to allow next start
        clock.advanceTime(200);
        assertEquals(2, scheduler.getInFlightCount());
        assertEquals(0, scheduler.getPendingCount());

        executor.runAll();
        assertEquals("res2", cb2.getResult());
        assertEquals("res3", cb3.getResult());
        assertEquals(0, scheduler.getInFlightCount());
    }

    @Test
    public void testLowPrioritySlotReservation() {
        FakeSchedulerClock clock = new FakeSchedulerClock();
        QueuedTestExecutor executor = new QueuedTestExecutor();
        RequestScheduler scheduler = new RequestScheduler(clock, executor);

        TestSchedulerAction aLow1 = new TestSchedulerAction("low1");
        TestSchedulerAction aLow2 = new TestSchedulerAction("low2");
        TestSchedulerAction aHigh = new TestSchedulerAction("high");

        TestSchedulerCallback cbLow1 = new TestSchedulerCallback();
        TestSchedulerCallback cbLow2 = new TestSchedulerCallback();
        TestSchedulerCallback cbHigh = new TestSchedulerCallback();

        // 1. Enqueue low priority task
        scheduler.enqueue(new RequestKey("low1"), RequestPriority.LOW, "subLow1", aLow1, cbLow1);
        assertEquals(1, scheduler.getInFlightCount());

        // 2. Enqueue second low priority task after gap
        clock.advanceTime(300);
        scheduler.enqueue(new RequestKey("low2"), RequestPriority.LOW, "subLow2", aLow2, cbLow2);

        // Second low priority task MUST NOT start because max non-high concurrency is 1!
        assertEquals(1, scheduler.getInFlightCount());
        assertEquals(1, scheduler.getPendingCount());

        // 3. Enqueue HIGH priority task after gap
        clock.advanceTime(300);
        scheduler.enqueue(new RequestKey("high1"), RequestPriority.HIGH, "subHigh", aHigh, cbHigh);

        // High priority task CAN use the reserved second slot!
        assertEquals(2, scheduler.getInFlightCount());
        assertEquals(1, scheduler.getPendingCount()); // low2 is still pending

        executor.runAll();
        assertEquals("low1", cbLow1.getResult());
        assertEquals("high", cbHigh.getResult());
        assertNull(cbLow2.getResult());

        // Now that slots are free, low2 can start after gap
        clock.advanceTime(300);
        assertEquals(1, scheduler.getInFlightCount());
        assertEquals(0, scheduler.getPendingCount());
        executor.runAll();
        assertEquals("low2", cbLow2.getResult());
    }

    @Test
    public void testDuplicateKeyDeduplicationAndPriorityBoost() {
        FakeSchedulerClock clock = new FakeSchedulerClock();
        QueuedTestExecutor executor = new QueuedTestExecutor();
        RequestScheduler scheduler = new RequestScheduler(clock, executor);

        TestSchedulerAction a1 = new TestSchedulerAction("data");
        TestSchedulerCallback cb1 = new TestSchedulerCallback();
        TestSchedulerCallback cb2 = new TestSchedulerCallback();

        // Enqueue key1 with LOW priority
        scheduler.enqueue(new RequestKey("k1"), RequestPriority.LOW, "sub1", a1, cb1);
        assertEquals(1, scheduler.getInFlightCount());

        // Enqueue same key1 with HIGH priority and sub2
        scheduler.enqueue(new RequestKey("k1"), RequestPriority.HIGH, "sub2", a1, cb2);

        // Still only 1 in flight, nothing pending
        assertEquals(1, scheduler.getInFlightCount());
        assertEquals(0, scheduler.getPendingCount());

        executor.runAll();
        // Both callbacks received the same result
        assertEquals("data", cb1.getResult());
        assertEquals("data", cb2.getResult());
        // Action executed only once
        assertEquals(1, a1.getExecutionCount());
    }

    @Test
    public void testSubscriberCancellationSeparated() {
        FakeSchedulerClock clock = new FakeSchedulerClock();
        QueuedTestExecutor executor = new QueuedTestExecutor();
        RequestScheduler scheduler = new RequestScheduler(clock, executor);

        TestSchedulerAction a1 = new TestSchedulerAction("data");
        TestSchedulerCallback cbA = new TestSchedulerCallback();
        TestSchedulerCallback cbB = new TestSchedulerCallback();

        scheduler.enqueue(new RequestKey("k1"), RequestPriority.LOW, "subA", a1, cbA);
        scheduler.enqueue(new RequestKey("k1"), RequestPriority.LOW, "subB", a1, cbB);

        // Subscriber A cancels
        scheduler.cancelSubscriber("subA", new RequestKey("k1"));
        assertTrue(cbA.isCancelled());
        assertFalse(cbB.isCancelled());

        // Task still executes for subscriber B
        executor.runAll();
        assertEquals("data", cbB.getResult());
    }

    @Test
    public void testRateLimitCooldown() {
        FakeSchedulerClock clock = new FakeSchedulerClock();
        QueuedTestExecutor executor = new QueuedTestExecutor();
        RequestScheduler scheduler = new RequestScheduler(clock, executor);

        // Set cooldown of 60 seconds
        scheduler.setCooldownSeconds(60);

        TestSchedulerAction a1 = new TestSchedulerAction("res");
        TestSchedulerCallback cb1 = new TestSchedulerCallback();

        scheduler.enqueue(new RequestKey("k1"), RequestPriority.HIGH, "sub1", a1, cb1);

        // Not started because cooldown is active
        assertEquals(0, scheduler.getInFlightCount());
        assertEquals(1, scheduler.getPendingCount());

        // Advance time 59s -> still blocked
        clock.advanceTime(59000);
        assertEquals(0, scheduler.getInFlightCount());

        // Advance 2s (total 61s) -> cooldown expired, timer triggers pump!
        clock.advanceTime(2000);
        assertEquals(1, scheduler.getInFlightCount());
        assertEquals(0, scheduler.getPendingCount());

        executor.runAll();
        assertEquals("res", cb1.getResult());
    }

    @Test
    public void testGlobalGapTiming() {
        FakeSchedulerClock clock = new FakeSchedulerClock();
        QueuedTestExecutor executor = new QueuedTestExecutor();
        RequestScheduler scheduler = new RequestScheduler(clock, executor);

        TestSchedulerAction a1 = new TestSchedulerAction("1");
        TestSchedulerAction a2 = new TestSchedulerAction("2");

        TestSchedulerCallback cb1 = new TestSchedulerCallback();
        TestSchedulerCallback cb2 = new TestSchedulerCallback();

        scheduler.enqueue(new RequestKey("k1"), RequestPriority.HIGH, "sub1", a1, cb1);
        assertEquals(1, scheduler.getInFlightCount());

        // Immediately enqueue task 2
        scheduler.enqueue(new RequestKey("k2"), RequestPriority.HIGH, "sub2", a2, cb2);

        // Gap constraint (160ms) prevents immediate start: k2 remains pending
        assertEquals(1, scheduler.getInFlightCount());
        assertEquals(1, scheduler.getPendingCount());

        // Advance 100ms (< 160ms) -> still pending
        clock.advanceTime(100);
        assertEquals(1, scheduler.getInFlightCount());
        assertEquals(1, scheduler.getPendingCount());

        // Advance 70ms (total 170ms >= 160ms) -> timer triggers pump and k2 starts!
        clock.advanceTime(70);
        assertEquals(2, scheduler.getInFlightCount());
        assertEquals(0, scheduler.getPendingCount());

        executor.runAll();
        assertEquals("1", cb1.getResult());
        assertEquals("2", cb2.getResult());
    }
}
