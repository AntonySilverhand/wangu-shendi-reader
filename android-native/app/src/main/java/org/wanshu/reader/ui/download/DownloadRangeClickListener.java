package org.wanshu.reader.ui.download;

import android.view.View;
import org.wanshu.reader.core.download.DownloadRange;

public class DownloadRangeClickListener implements View.OnClickListener {
    private final DownloadsDialog dialog;
    private final DownloadRange range;

    public DownloadRangeClickListener(DownloadsDialog dialog, DownloadRange range) {
        this.dialog = dialog;
        this.range = range;
    }

    @Override
    public void onClick(View v) {
        if (dialog != null && range != null) {
            dialog.onRangeSelected(range);
        }
    }
}
