package ict.um.orders.services.routing;

import ict.um.orders.core_api.config.QueueNames;
import ict.um.orders.ml.features.QueueFeatures;
import ict.um.orders.ml.features.RoutingFeatures;
import ict.um.orders.ml.metrics.RoutingMetricsCollector;
import ict.um.orders.routing.OrderRoutingContext;
import ict.um.orders.routing.RoutingDecision;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@ConditionalOnProperty(
        name = "routing.strategy",
        havingValue = "shortest-queue",
        matchIfMissing = true
)
public class ShortestQueueRoutingService implements RoutingService {

    private static final Logger logger =
            LoggerFactory.getLogger(ShortestQueueRoutingService.class);

    private final RoutingMetricsCollector metricsCollector;
    private final AtomicInteger tieIndex = new AtomicInteger();

    public ShortestQueueRoutingService(
            RoutingMetricsCollector metricsCollector
    ) {
        this.metricsCollector = metricsCollector;
    }

    @Override
    public RoutingDecision route(OrderRoutingContext context) {
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

            shortestQueueLength = updateShortestQueues(
                    shortestQueueLength,
                    tiedQueues,
                    queueKey,
                    queueFeatures
            );
        }

        if (tiedQueues.isEmpty()) {
            throw new IllegalStateException(
                    "No valid processing queue metrics are available"
            );
        }

        String selectedQueue = selectRoundRobin(tiedQueues);

        logger.info(
                "Shortest-queue routing: orderId={}, queue1Length={}, "
                        + "queue2Length={}, queue3Length={}, selectedQueue={}",
                context.orderId(),
                features.queues().get(QueueNames.QUEUE_1).queueLength(),
                features.queues().get(QueueNames.QUEUE_2).queueLength(),
                features.queues().get(QueueNames.QUEUE_3).queueLength(),
                selectedQueue
        );

        return new RoutingDecision(
                UUID.randomUUID().toString(),
                selectedQueue
        );
    }
    private double updateShortestQueues(
            double shortestQueueLength,
            List<String> tiedQueues,
            String queueKey,
            QueueFeatures queueFeatures
    ) {
        double queueLength = queueFeatures.queueLength();

        if (!Double.isFinite(queueLength) || queueLength < 0.0) {
            return shortestQueueLength;
        }

        int comparison =
                Double.compare(queueLength, shortestQueueLength);

        if (comparison < 0) {
            tiedQueues.clear();
            tiedQueues.add(queueKey);
            return queueLength;
        }

        if (comparison == 0) {
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
}