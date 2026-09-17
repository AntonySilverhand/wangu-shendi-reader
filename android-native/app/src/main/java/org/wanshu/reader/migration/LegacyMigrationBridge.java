package org.wanshu.reader.migration;

import android.webkit.JavascriptInterface;

public class LegacyMigrationBridge {
    private final LegacyMigrationListener listener;

    public LegacyMigrationBridge(LegacyMigrationListener listener) {
        this.listener = listener;
    }

    @JavascriptInterface
    public void onHandshake(String protocolVersion) {
        if (listener != null) {
            listener.onHandshake(protocolVersion);
        }
    }

    @JavascriptInterface
    public void onLocalStorageData(String settingsJson, String personalJson, String localBooksJson) {
        if (listener != null) {
            listener.onLocalStorage(settingsJson, personalJson, localBooksJson);
        }
    }

    @JavascriptInterface
    public void onIndexedDbBatch(
            String runId,
            String storeName,
            String batchJson,
            int batchIndex,
            boolean isLastBatch,
            String checksum
    ) {
        if (listener != null) {
            listener.onIndexedDbBatch(runId, storeName, batchJson, batchIndex, isLastBatch, checksum);
        }
    }

    @JavascriptInterface
    public void onMigrationComplete(String runId, String summaryJson) {
        if (listener != null) {
            listener.onComplete(runId, summaryJson);
        }
    }

    @JavascriptInterface
    public void onMigrationError(String runId, String errorStage, String errorMessage) {
        if (listener != null) {
            listener.onError(runId, errorStage, errorMessage);
        }
    }
}
