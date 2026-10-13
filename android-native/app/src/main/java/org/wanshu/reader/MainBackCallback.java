package org.wanshu.reader;

import androidx.activity.OnBackPressedCallback;
import org.wanshu.reader.navigation.BackController;

/** AndroidX dispatch also receives gesture Back on API 36; keyboard keeps platform priority. */
public final class MainBackCallback extends OnBackPressedCallback {
    private final MainActivity activity;
    private final BackController controller;
    public MainBackCallback(MainActivity activity, BackController controller) {
        super(true); this.activity = activity; this.controller = controller;
    }
    @Override public void handleOnBackPressed() {
        if (controller.handleBack()) return;
        setEnabled(false);
        activity.getOnBackPressedDispatcher().onBackPressed();
        setEnabled(true);
    }
}
