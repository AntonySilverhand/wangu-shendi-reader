package org.wanshu.reader.download;

import org.wanshu.reader.core.model.ChapterResult;
import org.wanshu.reader.core.queue.SchedulerCallback;
import org.wanshu.reader.data.content.entity.DownloadTaskEntity;

public class DownloadSchedulerCallback implements SchedulerCallback<ChapterResult> {
    private final DownloadCoordinator coordinator;
    private final DownloadTaskEntity task;
    private final long token;

    public DownloadSchedulerCallback(DownloadCoordinator coordinator, DownloadTaskEntity task, long token) {
        this.coordinator = coordinator;
        this.task = task;
        this.token = token;
    }

    @Override
    public void onSuccess(ChapterResult result) {
        if (coordinator != null) {
            coordinator.onChapterFetched(task, result, token);
        }
    }

    @Override
    public void onError(Throwable error) {
        if (coordinator != null) {
            coordinator.onChapterFetchFailed(task, error, token);
        }
    }

    @Override
    public void onCancelled() {
        if (coordinator != null) {
            coordinator.onChapterFetchCancelled(task, token);
        }
    }
}
