package org.wanshu.reader.ui.shelf;

import android.app.Activity;
import android.widget.Toast;

/** Main thread: shows a short message. */
public class ShelfMessageRunnable implements Runnable {
    private final Activity activity;
    private final String message;

    public ShelfMessageRunnable(Activity activity, String message) {
        this.activity = activity;
        this.message = message;
    }

    @Override
    public void run() {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }
}
