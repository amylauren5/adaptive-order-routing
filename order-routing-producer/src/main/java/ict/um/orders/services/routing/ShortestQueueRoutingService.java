package ict.um.orders.services.routing;

import ict.um.orders.core_api.config.QueueNames;
import ict.um.orders.ml.features.QueueFeatures;
import ict.um.orders.ml.features.RoutingFeatures;
import ict.um.orders.ml.metrics.RoutingMetricsCollector;
import ict.um.orders.routing.OrderRoutingContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@ConditionalOnProperty(
        name = "routing.strategy",
        havingValue = "shortest-queue",
        matchIfMissing = true
)
public class ShortestQueueRoutingService implements RoutingService {

    private final RoutingMetricsCollector metricsCollector;
    private final AtomicInteger tieIndex = new AtomicInteger();

    public ShortestQueueRoutingService(
            RoutingMetricsCollector metricsCollector
    ) {
        this.metricsCollector = metricsCollector;
    }

    @Override
    public String route(OrderRoutingContext context) {
        RoutingFeatures features = metricsCollector.collectAll();

        double shortestQueueLength = Double.POSITIVE_INFINITY;
        List<String> tiedQueues = new ArrayList<>();

        for (String queueKey : RoutingFeatures.QUEUE_ORDER) {
            QueueFeatures queueFeatures =
                    features.queues().get(queueKey);

            if (queueFeatures == null) {
                throw new IllegalStateException(
                        "Missing routing features for queue: " + queueKey
                );
            }

            shortestQueueLength = getShortestQueueLength(shortestQueueLength, tiedQueues, queueKey, queueFeatures);
        }

        if (tiedQueues.isEmpty()) {
            throw new IllegalStateException(
                    "No valid processing queue metrics are available"
            );
        }

        return toRabbitQueue(selectRoundRobin(tiedQueues));
    }

    static double getShortestQueueLength(double shortestQueueLength, List<String> tiedQueues, String queueKey, QueueFeatures queueFeatures) {
        double queueLength = queueFeatures.queueLength();

        if (!Double.isFinite(queueLength) || queueLength < 0.0) {
            return shortestQueueLength;
        }

        int comparison =
                Double.compare(queueLength, shortestQueueLength);

        if (comparison < 0) {
            shortestQueueLength = queueLength;
            tiedQueues.clear();
            tiedQueues.add(queueKey);
        } else if (comparison == 0) {
            tiedQueues.add(queueKey);
        }
        return shortestQueueLength;
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
            case "queue1" -> QueueNames.QUEUE_1;
            case "queue2" -> QueueNames.QUEUE_2;
            case "queue3" -> QueueNames.QUEUE_3;
            default -> throw new IllegalStateException(
                    "Unknown queue key: " + queueKey
            );
        };
    }
}