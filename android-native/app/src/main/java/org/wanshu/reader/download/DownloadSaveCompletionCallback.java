package org.wanshu.reader.download;

import org.wanshu.reader.data.repository.CompletionCallback;

public class DownloadSaveCompletionCallback implements CompletionCallback {
    private final DownloadCoordinator coordinator;

    public DownloadSaveCompletionCallback(DownloadCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @Override
    public void onSuccess() {
        if (coordinator != null) {
            coordinator.onChapterSaved();
        }
    }

    @Override
    public void onError(Throwable error) {
        if (coordinator != null) {
            coordinator.onChapterSaved();
        }
    }
}
