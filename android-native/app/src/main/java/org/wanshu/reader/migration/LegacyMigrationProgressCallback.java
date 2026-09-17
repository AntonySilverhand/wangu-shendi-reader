package org.wanshu.reader.migration;

public interface LegacyMigrationProgressCallback {
    void onProgress(String statusText, int current, int total);
    void onFinished(boolean success, String message);
}
