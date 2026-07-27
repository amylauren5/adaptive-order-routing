package ict.um.orders.ml.training;

import ict.um.orders.core_api.config.QueueNames;
import ict.um.orders.ml.features.QueueFeatures;
import ict.um.orders.ml.features.RoutingFeatures;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class LittleLawTargetGenerator {

    private final AtomicInteger tieIndex = new AtomicInteger();

    public String chooseTarget(RoutingFeatures features) {
        double lowestExpectedDelay = Double.POSITIVE_INFINITY;
        List<String> tiedQueues = new ArrayList<>();

        for (String queueKey : RoutingFeatures.QUEUE_ORDER) {
            QueueFeatures queue = features.queues().get(queueKey);
            double expectedDelay = calculateExpectedDelay(queue);

            int comparison =
                    Double.compare(expectedDelay, lowestExpectedDelay);

            if (comparison < 0) {
                lowestExpectedDelay = expectedDelay;
                tiedQueues.clear();
                tiedQueues.add(queueKey);
            } else if (comparison == 0) {
                tiedQueues.add(queueKey);
            }
        }

        if (tiedQueues.isEmpty()) {
            return chooseShortestQueue(features);
        }

        return toRabbitQueue(selectRoundRobin(tiedQueues));
    }

    private double calculateExpectedDelay(QueueFeatures queue) {
        if (queue.queueLength() == 0.0) {
            return 0.0;
        }

        if (queue.consumerThroughput() <= 0.0) {
            return Double.POSITIVE_INFINITY;
        }

        return queue.queueLength() / queue.consumerThroughput();
    }

    private String chooseShortestQueue(RoutingFeatures features) {
        double shortestLength = Double.POSITIVE_INFINITY;
        List<String> tiedQueues = new ArrayList<>();

        for (String queueKey : RoutingFeatures.QUEUE_ORDER) {
            QueueFeatures queue = features.queues().get(queueKey);
            double queueLength = queue.queueLength();

            int comparison =
                    Double.compare(queueLength, shortestLength);

            if (comparison < 0) {
                shortestLength = queueLength;
                tiedQueues.clear();
                tiedQueues.add(queueKey);
            } else if (comparison == 0) {
                tiedQueues.add(queueKey);
            }
        }

        if (tiedQueues.isEmpty()) {
            throw new IllegalStateException(
                    "No queue features available"
            );
        }

        return toRabbitQueue(selectRoundRobin(tiedQueues));
    }

    private String selectRoundRobin(List<String> queues) {
        int index = Math.floorMod(
                tieIndex.getAndIncrement(),
                queues.size()
        );

        return queues.get(index);
    }

    private String toRabbitQueue(String queueKey) {
        return switch (queueKey) {
            case "high" -> QueueNames.HIGH;
            case "medium" -> QueueNames.MEDIUM;
            case "low" -> QueueNames.LOW;
            default -> throw new IllegalStateException(
                    "Unknown queue key: " + queueKey
            );
        };
    }
}