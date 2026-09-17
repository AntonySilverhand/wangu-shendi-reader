package org.wanshu.reader.core.queue;

public class SubscriberSubscription {
    private final Object subscriberId;
    private final SchedulerCallback<?> callback;

    public SubscriberSubscription(Object subscriberId, SchedulerCallback<?> callback) {
        this.subscriberId = subscriberId;
        this.callback = callback;
    }

    public Object getSubscriberId() {
        return subscriberId;
    }

    @SuppressWarnings("unchecked")
    public <T> SchedulerCallback<T> getCallback() {
        return (SchedulerCallback<T>) callback;
    }
}
