package org.wanshu.reader.data.repository;

import org.wanshu.reader.data.personal.PersonalDatabase;
import org.wanshu.reader.data.personal.entity.LastRouteEntity;

public class SaveLastRouteRunnable implements Runnable {
    private final PersonalDatabase db;
    private final LastRouteEntity route;
    private final CompletionCallback callback;

    public SaveLastRouteRunnable(PersonalDatabase db, LastRouteEntity route, CompletionCallback callback) {
        this.db = db;
        this.route = route;
        this.callback = callback;
    }

    @Override
    public void run() {
        try {
            db.lastRouteDao().saveLastRoute(route);
            if (callback != null) {
                callback.onSuccess();
            }
        } catch (Throwable t) {
            if (callback != null) {
                callback.onError(t);
            }
        }
    }
}
