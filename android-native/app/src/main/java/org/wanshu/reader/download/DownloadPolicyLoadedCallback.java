package org.wanshu.reader.download;

import org.wanshu.reader.data.content.entity.DownloadPolicyEntity;

public interface DownloadPolicyLoadedCallback {
    void onPolicyLoaded(DownloadPolicyEntity policy);
}
