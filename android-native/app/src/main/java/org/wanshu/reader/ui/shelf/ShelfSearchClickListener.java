package org.wanshu.reader.ui.shelf;

import android.view.View;

public class ShelfSearchClickListener implements View.OnClickListener {
    private final ShelfController controller;

    public ShelfSearchClickListener(ShelfController controller) {
        this.controller = controller;
    }

    @Override
    public void onClick(View v) {
        controller.onSearchClicked();
    }
}
