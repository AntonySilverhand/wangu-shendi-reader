package org.wanshu.reader.data.content;

public class StorageStats {
    private final int completedCount;
    private final int partialCount;
    private final long totalBytes;

    public StorageStats(int completedCount, int partialCount, long totalBytes) {
        this.completedCount = completedCount;
        this.partialCount = partialCount;
        this.totalBytes = totalBytes;
    }

    public int getCompletedCount() {
        return completedCount;
    }

    public int getPartialCount() {
        return partialCount;
    }

    public long getTotalBytes() {
        return totalBytes;
    }
}
