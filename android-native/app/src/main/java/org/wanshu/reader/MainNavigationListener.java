package org.wanshu.reader;

import org.wanshu.reader.navigation.BookRoute;
import org.wanshu.reader.navigation.NavigationListener;

public class MainNavigationListener implements NavigationListener {
    private final MainActivity activity;

    public MainNavigationListener(MainActivity activity) {
        this.activity = activity;
    }

    @Override
    public void onRouteChanged(BookRoute route) {
        activity.handleRouteChanged(route);
    }
}
