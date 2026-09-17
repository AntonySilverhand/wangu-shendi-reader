package org.wanshu.reader.core.download;

public class DownloadTaskState {
    public static final String PENDING = "PENDING";
    public static final String RUNNING = "RUNNING";
    public static final String COMPLETE = "COMPLETE";
    public static final String RETRY_AT = "RETRY_AT";
    public static final String NEEDS_ACTION = "NEEDS_ACTION";
    public static final String CANCELLED = "CANCELLED";
}
