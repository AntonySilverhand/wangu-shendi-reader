package org.wanshu.reader;

import org.wanshu.reader.data.personal.entity.LastRouteEntity;
import org.wanshu.reader.data.repository.DataCallback;

public class MainLastRouteCallback implements DataCallback<LastRouteEntity> {
    private final MainActivity activity;

    public MainLastRouteCallback(MainActivity activity) {
        this.activity = activity;
    }

    @Override
    public void onSuccess(LastRouteEntity data) {
        activity.handleLastRoute(data);
    }

    @Override
    public void onError(Throwable error) {
        activity.handleLastRoute(null);
    }
}
