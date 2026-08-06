package ict.um.orders.ml.features;

import java.util.List;
import java.util.Map;

public class RoutingFeatures {

    public static final List<String> QUEUE_ORDER =
            List.of("queue1", "queue2", "queue3");

    private final Map<String, QueueFeatures> queues;

    public RoutingFeatures(Map<String, QueueFeatures> queues) {
        this.queues = Map.copyOf(queues);

        for (String queueName : QUEUE_ORDER) {
            if (!this.queues.containsKey(queueName)) {
                throw new IllegalArgumentException(
                        "Missing features for queue: " + queueName
                );
            }
        }
    }

    public Map<String, QueueFeatures> queues() {
        return queues;
    }

}