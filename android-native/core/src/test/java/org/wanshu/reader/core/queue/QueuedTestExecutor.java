package org.wanshu.reader.core.queue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

public class QueuedTestExecutor extends AbstractExecutorService {

    private final List<Runnable> queue = new ArrayList<Runnable>();
    private boolean shutdown = false;

    @Override
    public synchronized void execute(Runnable command) {
        if (command != null && !shutdown) {
            queue.add(command);
        }
    }

    public synchronized int getQueueSize() {
        return queue.size();
    }

    public synchronized boolean runNext() {
        if (queue.isEmpty()) {
            return false;
        }
        Runnable r = queue.remove(0);
        r.run();
        return true;
    }

    public synchronized int runAll() {
        int count = 0;
        while (!queue.isEmpty()) {
            queue.remove(0).run();
            count++;
        }
        return count;
    }

    @Override
    public synchronized void shutdown() {
        shutdown = true;
    }

    @Override
    public synchronized List<Runnable> shutdownNow() {
        shutdown = true;
        List<Runnable> remaining = new ArrayList<Runnable>(queue);
        queue.clear();
        return remaining;
    }

    @Override
    public synchronized boolean isShutdown() {
        return shutdown;
    }

    @Override
    public synchronized boolean isTerminated() {
        return shutdown && queue.isEmpty();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
        return true;
    }
}
