package org.wanshu.reader.core.download;

public class RetryPolicy {
    public static final int MAX_ATTEMPTS = 5;

    public static long getBackoffDelayMillis(int attempts) {
        switch (attempts) {
            case 0:
                return 5000L;
            case 1:
                return 30000L;
            case 2:
                return 120000L;
            case 3:
                return 600000L;
            case 4:
                return 1800000L;
            default:
                return -1L;
        }
    }
}
