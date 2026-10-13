package org.wanshu.reader.data.repository;

/** Called on the content executor, never perform widget work directly. */
public interface TocLoadListener {
    void onProgress(int loaded, int total, boolean finished, String error);
}
