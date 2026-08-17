package ict.um.orders.services.routing;

import ict.um.orders.core_api.config.QueueNames;
import ict.um.orders.ml.features.RoutingFeatures;
import ict.um.orders.evaluation.RoutingMetricsCollector;
import ict.um.orders.ml.training.TrainingDataLogger;
import ict.um.orders.routing.OrderRoutingContext;
import ict.um.orders.routing.RoutingDecision;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
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
    public RoutingDecision route(OrderRoutingContext context) {
        /*
         * Capture all input features before the routing decision
         * to avoid temporal leakage.
         */
        RoutingFeatures features = metricsCollector.collectAll();

        String selectedQueue = selectNextQueue();
        String routingDecisionId = UUID.randomUUID().toString();

        /*
         * Store an incomplete observation. The consumer will later
         * complete it with the realised queue waiting time.
         */
        trainingLogger.logPending(
                routingDecisionId,
                context,
                features,
                selectedQueue
        );

        return new RoutingDecision(
                routingDecisionId,
                selectedQueue
        );
    }

    private String selectNextQueue() {
        int index = Math.floorMod(
                nextQueueIndex.getAndIncrement(),
                PROCESSING_QUEUES.size()
        );

        return PROCESSING_QUEUES.get(index);
    }
}