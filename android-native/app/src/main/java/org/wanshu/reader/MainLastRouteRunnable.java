package org.wanshu.reader;

import org.wanshu.reader.data.personal.entity.LastRouteEntity;

public class MainLastRouteRunnable implements Runnable {
    private final MainActivity activity;
    private final LastRouteEntity route;

    public MainLastRouteRunnable(MainActivity activity, LastRouteEntity route) {
        this.activity = activity;
        this.route = route;
    }

    @Override
    public void run() {
        activity.applyLastRoute(route);
    }
}
