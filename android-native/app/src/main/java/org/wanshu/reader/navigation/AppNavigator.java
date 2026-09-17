package org.wanshu.reader.navigation;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public class AppNavigator {
    private final AtomicLong generationSequence = new AtomicLong(1L);
    private volatile BookRoute currentRoute;
    private final List<NavigationListener> listeners = new CopyOnWriteArrayList<NavigationListener>();

    public AppNavigator() {
        this.currentRoute = BookRoute.shelf(generationSequence.get());
    }

    public void addListener(NavigationListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(NavigationListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    public BookRoute getCurrentRoute() {
        return currentRoute;
    }

    public long getCurrentGeneration() {
        return generationSequence.get();
    }

    public boolean isCurrentGeneration(long generation) {
        return generationSequence.get() == generation;
    }

    public BookRoute openShelf() {
        long gen = generationSequence.incrementAndGet();
        BookRoute route = BookRoute.shelf(gen);
        this.currentRoute = route;
        notifyListeners(route);
        return route;
    }

    public BookRoute openReader(String bookId, String chapterId) {
        long gen = generationSequence.incrementAndGet();
        BookRoute route = BookRoute.reader(bookId, chapterId, gen);
        this.currentRoute = route;
        notifyListeners(route);
        return route;
    }

    public BookRoute openReaderWithAnchor(
            String bookId,
            String chapterId,
            int paragraphIndex,
            int offsetUtf16
    ) {
        long gen = generationSequence.incrementAndGet();
        BookRoute route = BookRoute.readerWithAnchor(bookId, chapterId, paragraphIndex, offsetUtf16, gen);
        this.currentRoute = route;
        notifyListeners(route);
        return route;
    }

    private void notifyListeners(BookRoute route) {
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).onRouteChanged(route);
        }
    }
}
