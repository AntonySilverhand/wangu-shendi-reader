package org.wanshu.reader.ui.shelf;

import android.app.Activity;
import android.widget.Toast;

public class ShelfImportErrorRunnable implements Runnable {
    private final Activity activity;
    private final Throwable error;

    public ShelfImportErrorRunnable(Activity activity, Throwable error) {
        this.activity = activity;
        this.error = error;
    }

    @Override
    public void run() {
        String msg = error != null ? error.getMessage() : "未知错误";
        Toast.makeText(activity, "导入失败: " + msg, Toast.LENGTH_SHORT).show();
    }
}
