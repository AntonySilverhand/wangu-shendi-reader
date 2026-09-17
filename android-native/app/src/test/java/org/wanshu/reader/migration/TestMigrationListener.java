package org.wanshu.reader.migration;

public class TestMigrationListener implements LegacyMigrationListener {
    private String handshakeVersion;
    private String settingsJson;
    private String personalJson;
    private String localBooksJson;
    private String lastRunId;
    private String lastStoreName;
    private String lastBatchJson;
    private int lastBatchIndex = -1;
    private boolean lastIsLastBatch;
    private String completedSummaryJson;
    private String errorStage;
    private String errorMessage;

    @Override
    public void onHandshake(String protocolVersion) {
        this.handshakeVersion = protocolVersion;
    }

    @Override
    public void onLocalStorage(String settingsJson, String personalJson, String localBooksJson) {
        this.settingsJson = settingsJson;
        this.personalJson = personalJson;
        this.localBooksJson = localBooksJson;
    }

    @Override
    public void onIndexedDbBatch(
            String runId,
            String storeName,
            String batchJson,
            int batchIndex,
            boolean isLastBatch,
            String checksum
    ) {
        this.lastRunId = runId;
        this.lastStoreName = storeName;
        this.lastBatchJson = batchJson;
        this.lastBatchIndex = batchIndex;
        this.lastIsLastBatch = isLastBatch;
    }

    @Override
    public void onComplete(String runId, String summaryJson) {
        this.lastRunId = runId;
        this.completedSummaryJson = summaryJson;
    }

    @Override
    public void onError(String runId, String errorStage, String errorMessage) {
        this.lastRunId = runId;
        this.errorStage = errorStage;
        this.errorMessage = errorMessage;
    }

    public String getHandshakeVersion() {
        return handshakeVersion;
    }

    public String getSettingsJson() {
        return settingsJson;
    }

    public String getPersonalJson() {
        return personalJson;
    }

    public String getLocalBooksJson() {
        return localBooksJson;
    }

    public String getLastStoreName() {
        return lastStoreName;
    }

    public String getLastBatchJson() {
        return lastBatchJson;
    }

    public int getLastBatchIndex() {
        return lastBatchIndex;
    }

    public boolean isLastIsLastBatch() {
        return lastIsLastBatch;
    }

    public String getCompletedSummaryJson() {
        return completedSummaryJson;
    }

    public String getErrorStage() {
        return errorStage;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
