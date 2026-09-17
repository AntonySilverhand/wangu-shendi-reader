package org.wanshu.reader.core.queue;

import java.util.ArrayList;
import java.util.List;

public class ScheduledTask {
    private final RequestKey key;
    private RequestPriority priority;
    private final long sequenceNumber;
    private final SchedulerAction<?> action;
    private final List<SubscriberSubscription> subscriptions = new ArrayList<SubscriberSubscription>();

    public ScheduledTask(
            RequestKey key,
            RequestPriority priority,
            long sequenceNumber,
            SchedulerAction<?> action,
            Object initialSubscriberId,
            SchedulerCallback<?> initialCallback
    ) {
        this.key = key;
        this.priority = priority;
        this.sequenceNumber = sequenceNumber;
        this.action = action;
        if (initialSubscriberId != null) {
            subscriptions.add(new SubscriberSubscription(initialSubscriberId, initialCallback));
        }
    }

    public RequestKey getKey() {
        return key;
    }

    public RequestPriority getPriority() {
        return priority;
    }

    public void boostPriority(RequestPriority newPriority) {
        if (newPriority != null && newPriority.getLevel() > this.priority.getLevel()) {
            this.priority = newPriority;
        }
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public SchedulerAction<?> getAction() {
        return action;
    }

    public List<SubscriberSubscription> getSubscriptions() {
        return subscriptions;
    }

    public void addSubscription(Object subscriberId, SchedulerCallback<?> callback) {
        if (subscriberId == null) return;
        for (int i = 0; i < subscriptions.size(); i++) {
            if (subscriptions.get(i).getSubscriberId().equals(subscriberId)) {
                return; // Already subscribed
            }
        }
        subscriptions.add(new SubscriberSubscription(subscriberId, callback));
    }

    public boolean removeSubscriber(Object subscriberId) {
        if (subscriberId == null) return subscriptions.isEmpty();
        for (int i = 0; i < subscriptions.size(); i++) {
            if (subscriptions.get(i).getSubscriberId().equals(subscriberId)) {
                SubscriberSubscription sub = subscriptions.remove(i);
                if (sub.getCallback() != null) {
                    try {
                        sub.getCallback().onCancelled();
                    } catch (Exception ignored) {}
                }
                break;
            }
        }
        return subscriptions.isEmpty();
    }
}
