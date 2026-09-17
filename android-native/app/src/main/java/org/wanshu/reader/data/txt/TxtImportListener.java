package org.wanshu.reader.data.txt;

public interface TxtImportListener {
    void onProgress(int chaptersCount, long bytesRead);
    void onSuccess(String bookId, int totalChapters, long totalBytes);
    void onError(Throwable error);
}
