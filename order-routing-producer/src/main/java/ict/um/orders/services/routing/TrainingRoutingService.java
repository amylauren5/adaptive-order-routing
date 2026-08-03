package ict.um.orders.services.routing;

import ict.um.orders.core_api.config.QueueNames;
import ict.um.orders.ml.features.RoutingFeatures;
import ict.um.orders.ml.metrics.RoutingMetricsCollector;
import ict.um.orders.ml.training.TrainingDataLogger;
import ict.um.orders.routing.OrderRoutingContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@ConditionalOnProperty(
        name = "routing.strategy",
        havingValue = "training"
)
public class TrainingRoutingService implements RoutingService {

    private static final List<String> PROCESSING_QUEUES = List.of(
            QueueNames.QUEUE_1,
            QueueNames.QUEUE_2,
            QueueNames.QUEUE_3
    );

    private final RoutingMetricsCollector metricsCollector;
    private final TrainingDataLogger trainingLogger;
    private final AtomicInteger nextQueueIndex = new AtomicInteger();

    public TrainingRoutingService(
            RoutingMetricsCollector metricsCollector,
            TrainingDataLogger trainingLogger
    ) {
        this.metricsCollector = metricsCollector;
        this.trainingLogger = trainingLogger;
    }

    @Override
    public String route(OrderRoutingContext context) {
        /*
         * Capture only metrics available before the routing decision.
         * These become the model input features.
         */
        RoutingFeatures features = metricsCollector.collectAll();

        /*
         * Controlled round-robin assignment provides observations from
         * every processing queue without using Little's Law as a teacher.
         */
        String selectedQueue = selectNextQueue();

        /*
         * This record is incomplete until the consumer reports the
         * realised waiting time for the routed event.
         */
        trainingLogger.log(features, selectedQueue);

        return selectedQueue;
    }

    private String selectNextQueue() {
        int index = Math.floorMod(
                nextQueueIndex.getAndIncrement(),
                PROCESSING_QUEUES.size()
        );

        return PROCESSING_QUEUES.get(index);
    }
}