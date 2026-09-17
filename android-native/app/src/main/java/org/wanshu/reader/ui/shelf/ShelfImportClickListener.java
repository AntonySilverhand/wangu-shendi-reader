package org.wanshu.reader.ui.shelf;

import android.view.View;

public class ShelfImportClickListener implements View.OnClickListener {
    private final ShelfController controller;

    public ShelfImportClickListener(ShelfController controller) {
        this.controller = controller;
    }

    @Override
    public void onClick(View v) {
        controller.onImportClicked();
    }
}
