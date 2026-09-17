package org.wanshu.reader.ui.shelf;

import java.util.List;

public class ShelfDisplayBooksRunnable implements Runnable {
    private final ShelfView view;
    private final List<ShelfItemModel> items;
    private final ShelfController controller;

    public ShelfDisplayBooksRunnable(ShelfView view, List<ShelfItemModel> items, ShelfController controller) {
        this.view = view;
        this.items = items;
        this.controller = controller;
    }

    @Override
    public void run() {
        view.displayBooks(items, controller);
    }
}
