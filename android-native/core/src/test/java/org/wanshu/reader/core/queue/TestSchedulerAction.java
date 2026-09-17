package org.wanshu.reader.core.queue;

import org.wanshu.reader.core.net.CancellationToken;

public class TestSchedulerAction implements SchedulerAction<String> {

    private final String result;
    private final Exception error;
    private int executionCount = 0;

    public TestSchedulerAction(String result, Exception error) {
        this.result = result;
        this.error = error;
    }

    public TestSchedulerAction(String result) {
        this(result, null);
    }

    public TestSchedulerAction(Exception error) {
        this(null, error);
    }

    @Override
    public String execute(CancellationToken token) throws Exception {
        executionCount++;
        if (error != null) {
            throw error;
        }
        return result;
    }

    public int getExecutionCount() {
        return executionCount;
    }
}
