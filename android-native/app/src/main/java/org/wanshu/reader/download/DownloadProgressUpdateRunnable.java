package org.wanshu.reader.download;

import java.util.List;

public class DownloadProgressUpdateRunnable implements Runnable {
    private final List<DownloadProgressListener> listeners;
    private final DownloadProgress progress;

    public DownloadProgressUpdateRunnable(List<DownloadProgressListener> listeners, DownloadProgress progress) {
        this.listeners = listeners;
        this.progress = progress;
    }

    @Override
    public void run() {
        if (listeners != null && progress != null) {
            for (int i = 0; i < listeners.size(); i++) {
                DownloadProgressListener listener = listeners.get(i);
                if (listener != null) {
                    listener.onDownloadProgress(progress);
                }
            }
        }
    }
}
