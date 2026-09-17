package org.wanshu.reader.download;

import org.wanshu.reader.data.content.entity.DownloadPolicyEntity;

public class DownloadPolicyDeliverRunnable implements Runnable {
    private final DownloadPolicyLoadedCallback callback;
    private final DownloadPolicyEntity policy;

    public DownloadPolicyDeliverRunnable(DownloadPolicyLoadedCallback callback, DownloadPolicyEntity policy) {
        this.callback = callback;
        this.policy = policy;
    }

    @Override
    public void run() {
        if (callback != null) {
            callback.onPolicyLoaded(policy);
        }
    }
}
