package org.wanshu.reader.ui.toc;

import org.wanshu.reader.data.content.ChapterCacheStatus;
import org.wanshu.reader.data.repository.DataCallback;

public class TocCacheStatusLoadedCallback implements DataCallback<ChapterCacheStatus> {
    private final TocDialog dialog;

    public TocCacheStatusLoadedCallback(TocDialog dialog) {
        this.dialog = dialog;
    }

    @Override
    public void onSuccess(ChapterCacheStatus result) {
        if (dialog != null) {
            dialog.onCacheStatusLoaded(result);
        }
    }

    @Override
    public void onError(Throwable error) {
        // Fallback: cache status failed, proceed with uncached markers
        if (dialog != null) {
            dialog.onCacheStatusLoaded(null);
        }
    }
}
