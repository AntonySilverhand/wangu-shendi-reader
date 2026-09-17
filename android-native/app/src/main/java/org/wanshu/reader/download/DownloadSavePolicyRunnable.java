package org.wanshu.reader.download;

import org.wanshu.reader.core.download.DownloadDemandFlags;
import org.wanshu.reader.data.content.ContentDatabase;
import org.wanshu.reader.data.content.entity.DownloadPolicyEntity;

public class DownloadSavePolicyRunnable implements Runnable {

    private final DownloadCoordinator coordinator;
    private final ContentDatabase contentDb;
    private final DownloadPolicyEntity policy;

    public DownloadSavePolicyRunnable(
            DownloadCoordinator coordinator,
            ContentDatabase contentDb,
            DownloadPolicyEntity policy
    ) {
        this.coordinator = coordinator;
        this.contentDb = contentDb;
        this.policy = policy;
    }

    @Override
    public void run() {
        if (policy == null || policy.bookId == null) {
            return;
        }

        contentDb.downloadPolicyDao().savePolicy(policy);

        if (!policy.autoCacheEnabled) {
            contentDb.downloadTaskDao().removeDemandFlag(policy.bookId, DownloadDemandFlags.DEMAND_AUTO);
            contentDb.downloadTaskDao().deleteOrphanedTasks(policy.bookId);
        }

        if (coordinator != null) {
            coordinator.onPolicyChanged(policy.bookId);
        }
    }
}
