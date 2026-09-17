package org.wanshu.reader.migration;

public interface LegacyMigrationListener {
    void onHandshake(String protocolVersion);

    void onLocalStorage(String settingsJson, String personalJson, String localBooksJson);

    void onIndexedDbBatch(
            String runId,
            String storeName,
            String batchJson,
            int batchIndex,
            boolean isLastBatch,
            String checksum
    );

    void onComplete(String runId, String summaryJson);

    void onError(String runId, String errorStage, String errorMessage);
}
