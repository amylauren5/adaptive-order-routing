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
        havingValue = "little-law"
)
public class LittleLawRoutingService implements RoutingService {

    private static final Logger logger =
            LoggerFactory.getLogger(LittleLawRoutingService.class);

    private final RoutingMetricsCollector metricsCollector;
    private final AtomicInteger tieIndex = new AtomicInteger();

    public LittleLawRoutingService(
            RoutingMetricsCollector metricsCollector
    ) {
        this.metricsCollector = metricsCollector;
    }

    @Override
    public RoutingDecision route(OrderRoutingContext context) {
        RoutingFeatures features = metricsCollector.collectAll();

        double queue1DelaySeconds =
                calculateExpectedDelay(
                        getQueueFeatures(features, QueueNames.QUEUE_1)
                );

        double queue2DelaySeconds =
                calculateExpectedDelay(
                        getQueueFeatures(features, QueueNames.QUEUE_2)
                );

        double queue3DelaySeconds =
                calculateExpectedDelay(
                        getQueueFeatures(features, QueueNames.QUEUE_3)
                );

        String selectedQueue = chooseQueue(features);

        logger.info(
                "Little-law routing: orderId={}, "
                        + "queue1DelaySeconds={}, "
                        + "queue2DelaySeconds={}, "
                        + "queue3DelaySeconds={}, "
                        + "selectedQueue={}",
                context.orderId(),
                queue1DelaySeconds,
                queue2DelaySeconds,
                queue3DelaySeconds,
                selectedQueue
        );

        return new RoutingDecision(
                UUID.randomUUID().toString(),
                selectedQueue
        );
    }

    String chooseQueue(RoutingFeatures features) {
        validateFeatures(features);

        double lowestExpectedDelay = Double.POSITIVE_INFINITY;
        List<String> tiedQueues = new ArrayList<>();
        boolean finiteEstimateAvailable = false;

        for (String queueKey : RoutingFeatures.QUEUE_ORDER) {
            QueueFeatures queueFeatures =
                    getQueueFeatures(features, queueKey);

            double expectedDelay =
                    calculateExpectedDelay(queueFeatures);

            if (!Double.isFinite(expectedDelay)) {
                continue;
            }

            finiteEstimateAvailable = true;

            int comparison =
                    Double.compare(
                            expectedDelay,
                            lowestExpectedDelay
                    );

            if (comparison < 0) {
                lowestExpectedDelay = expectedDelay;
                tiedQueues.clear();
                tiedQueues.add(queueKey);
            } else if (comparison == 0) {
                tiedQueues.add(queueKey);
            }
        }

        if (!finiteEstimateAvailable || tiedQueues.isEmpty()) {
            return chooseShortestQueue(features);
        }

        return selectRoundRobin(tiedQueues);
    }

    private double calculateExpectedDelay(
            QueueFeatures queueFeatures
    ) {
        double queueLength = queueFeatures.queueLength();
        double throughput = queueFeatures.consumerThroughput();

        if (!Double.isFinite(queueLength) || queueLength < 0.0) {
            return Double.POSITIVE_INFINITY;
        }

        if (queueLength == 0.0) {
            return 0.0;
        }

        if (!Double.isFinite(throughput) || throughput <= 0.0) {
            return Double.POSITIVE_INFINITY;
        }

        return queueLength / throughput;
    }

    private String chooseShortestQueue(
            RoutingFeatures features
    ) {
        double shortestLength = Double.POSITIVE_INFINITY;
        List<String> tiedQueues = new ArrayList<>();

        for (String queueKey : RoutingFeatures.QUEUE_ORDER) {
            QueueFeatures queueFeatures =
                    getQueueFeatures(features, queueKey);

            double queueLength = queueFeatures.queueLength();

            if (!Double.isFinite(queueLength) || queueLength < 0.0) {
                continue;
            }

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
                    "No valid queue metrics are available"
            );
        }

        return selectRoundRobin(tiedQueues);
    }

    private void validateFeatures(RoutingFeatures features) {
        if (features == null || features.queues() == null) {
            throw new IllegalArgumentException(
                    "Routing features and queue features must not be null"
            );
        }
    }

    private QueueFeatures getQueueFeatures(
            RoutingFeatures features,
            String queueKey
    ) {
        QueueFeatures queueFeatures =
                features.queues().get(queueKey);

        if (queueFeatures == null) {
            throw new IllegalStateException(
                    "Missing routing features for queue: " + queueKey
            );
        }

        return queueFeatures;
    }

    private String selectRoundRobin(List<String> queueKeys) {
        int index = Math.floorMod(
                tieIndex.getAndIncrement(),
                queueKeys.size()
        );

        return queueKeys.get(index);
    }
}