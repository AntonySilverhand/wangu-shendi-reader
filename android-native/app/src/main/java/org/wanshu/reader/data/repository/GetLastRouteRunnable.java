package org.wanshu.reader.data.repository;

import org.wanshu.reader.data.personal.PersonalDatabase;
import org.wanshu.reader.data.personal.entity.LastRouteEntity;

public class GetLastRouteRunnable implements Runnable {
    private final PersonalDatabase db;
    private final DataCallback<LastRouteEntity> callback;

    public GetLastRouteRunnable(PersonalDatabase db, DataCallback<LastRouteEntity> callback) {
        this.db = db;
        this.callback = callback;
    }

    @Override
    public void run() {
        try {
            LastRouteEntity route = db.lastRouteDao().getLastRoute();
            if (callback != null) {
                callback.onSuccess(route);
            }
        } catch (Throwable t) {
            if (callback != null) {
                callback.onError(t);
            }
        }
    }
}
