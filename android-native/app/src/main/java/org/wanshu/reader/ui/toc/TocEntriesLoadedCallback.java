package org.wanshu.reader.ui.toc;

import java.util.List;
import org.wanshu.reader.data.content.entity.TocEntryEntity;
import org.wanshu.reader.data.repository.DataCallback;

public class TocEntriesLoadedCallback implements DataCallback<List<TocEntryEntity>> {
    private final TocDialog dialog;

    public TocEntriesLoadedCallback(TocDialog dialog) {
        this.dialog = dialog;
    }

    @Override
    public void onSuccess(List<TocEntryEntity> result) {
        if (dialog != null) {
            dialog.onTocEntriesLoaded(result);
        }
    }

    @Override
    public void onError(Throwable error) {
        if (dialog != null) {
            dialog.onTocLoadError(error != null ? error.getMessage() : "加载目录失败");
        }
    }
}
