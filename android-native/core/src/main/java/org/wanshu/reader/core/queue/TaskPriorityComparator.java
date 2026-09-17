package org.wanshu.reader.core.queue;

import java.util.Comparator;

public class TaskPriorityComparator implements Comparator<ScheduledTask> {

    @Override
    public int compare(ScheduledTask a, ScheduledTask b) {
        int pDiff = Integer.compare(b.getPriority().getLevel(), a.getPriority().getLevel());
        if (pDiff != 0) {
            return pDiff;
        }
        return Long.compare(a.getSequenceNumber(), b.getSequenceNumber());
    }
}
