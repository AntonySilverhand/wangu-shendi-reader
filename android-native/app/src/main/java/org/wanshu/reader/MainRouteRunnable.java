package org.wanshu.reader;

import org.wanshu.reader.navigation.BookRoute;

public class MainRouteRunnable implements Runnable {
    private final MainActivity activity;
    private final BookRoute route;

    public MainRouteRunnable(MainActivity activity, BookRoute route) {
        this.activity = activity;
        this.route = route;
    }

    @Override
    public void run() {
        activity.applyRoute(route);
    }
}
