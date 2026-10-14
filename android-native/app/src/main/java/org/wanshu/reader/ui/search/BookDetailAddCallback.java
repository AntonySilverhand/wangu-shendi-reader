package org.wanshu.reader.ui.search;

import org.wanshu.reader.data.repository.CompletionCallback;

/** Result of "add to shelf" for the detail dialog. */
public class BookDetailAddCallback implements CompletionCallback {
    private final BookDetailDialog dialog;

    public BookDetailAddCallback(BookDetailDialog dialog) {
        this.dialog = dialog;
    }

    @Override
    public void onSuccess() {
        dialog.postToUi(new BookDetailShelfStateRunnable(dialog, true, "已加入书架"));
    }

    @Override
    public void onError(Throwable error) {
        String reason = error != null && error.getMessage() != null ? error.getMessage() : "未知错误";
        dialog.postToUi(new BookDetailShelfStateRunnable(dialog, false, "加入书架失败：" + reason));
    }
}
