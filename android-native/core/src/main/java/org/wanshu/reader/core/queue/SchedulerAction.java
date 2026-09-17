package org.wanshu.reader.core.queue;

import org.wanshu.reader.core.net.CancellationToken;

public interface SchedulerAction<T> {
    T execute(CancellationToken token) throws Exception;
}
