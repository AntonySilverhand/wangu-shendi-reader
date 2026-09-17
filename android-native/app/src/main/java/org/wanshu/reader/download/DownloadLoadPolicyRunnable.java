package org.wanshu.reader.download;

import java.util.concurrent.Executor;
import org.wanshu.reader.data.content.ContentDatabase;
import org.wanshu.reader.data.content.entity.DownloadPolicyEntity;

public class DownloadLoadPolicyRunnable implements Runnable {
    private final ContentDatabase contentDb;
    private final String bookId;
    private final Executor mainExecutor;
    private final DownloadPolicyLoadedCallback callback;

    public DownloadLoadPolicyRunnable(
            ContentDatabase contentDb,
            String bookId,
            Executor mainExecutor,
            DownloadPolicyLoadedCallback callback
    ) {
        this.contentDb = contentDb;
        this.bookId = bookId;
        this.mainExecutor = mainExecutor;
        this.callback = callback;
    }

    @Override
    public void run() {
        DownloadPolicyEntity policy = contentDb.downloadPolicyDao().getPolicy(bookId);
        if (policy == null) {
            policy = new DownloadPolicyEntity();
            policy.bookId = bookId != null ? bookId : "36780";
            policy.autoCacheEnabled = false;
            policy.scope = "ENTIRE_BOOK";
            policy.allowMetered = false;
            policy.maxCacheBytes = 256L * 1024L * 1024L;
        }
        if (callback != null && mainExecutor != null) {
            mainExecutor.execute(new DownloadPolicyDeliverRunnable(callback, policy));
        }
    }
}
