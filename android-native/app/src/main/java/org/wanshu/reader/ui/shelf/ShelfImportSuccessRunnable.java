package org.wanshu.reader.ui.shelf;

import android.app.Activity;
import android.widget.Toast;

public class ShelfImportSuccessRunnable implements Runnable {
    private final Activity activity;
    private final ShelfController controller;
    private final int chaptersCount;

    public ShelfImportSuccessRunnable(Activity activity, ShelfController controller, int chaptersCount) {
        this.activity = activity;
        this.controller = controller;
        this.chaptersCount = chaptersCount;
    }

    @Override
    public void run() {
        Toast.makeText(activity, "成功导入 " + chaptersCount + " 章", Toast.LENGTH_SHORT).show();
        controller.refresh();
    }
}
