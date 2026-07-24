package ict.um.orders.ml.features;

import java.util.Arrays;
import java.util.Map;

public class RoutingFeatures {

    private final Map<String, QueueFeatures> queues;

    public RoutingFeatures(Map<String, QueueFeatures> queues) {
        this.queues = queues;
    }

    public Map<String, QueueFeatures> queues() {
        return queues;
    }

    public double[] toVector() {
        return queues.values().stream()
                .flatMapToDouble(q -> Arrays.stream(new double[]{
                        q.queueLength(),
                        q.consumerThroughput(),
                        q.arrivalInterval(),
                        q.utilisation(),
                        q.backlogGrowth(),
                        q.tailLatency()
                }))
                .toArray();
    }
}
