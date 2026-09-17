package org.wanshu.reader.navigation;

import java.util.ArrayList;
import java.util.List;

public class TestNavigationListener implements NavigationListener {
    private final List<BookRoute> routes = new ArrayList<BookRoute>();

    @Override
    public void onRouteChanged(BookRoute route) {
        routes.add(route);
    }

    public List<BookRoute> getRoutes() {
        return routes;
    }

    public BookRoute getLastRoute() {
        return routes.isEmpty() ? null : routes.get(routes.size() - 1);
    }
}
