package ict.um.orders.services.routing;

import ict.um.orders.ml.features.RoutingFeatures;
import ict.um.orders.ml.metrics.RoutingMetricsCollector;
import ict.um.orders.ml.training.LittleLawTargetGenerator;
import ict.um.orders.ml.training.TrainingDataLogger;
import ict.um.orders.routing.OrderRoutingContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
        name = "routing.strategy",
        havingValue = "training"
)
public class TrainingRoutingService implements RoutingService {

    private final RoutingMetricsCollector metricsCollector;
    private final LittleLawTargetGenerator targetGenerator;
    private final TrainingDataLogger trainingLogger;

    public TrainingRoutingService(
            RoutingMetricsCollector metricsCollector,
            LittleLawTargetGenerator targetGenerator,
            TrainingDataLogger trainingLogger
    ) {
        this.metricsCollector = metricsCollector;
        this.targetGenerator = targetGenerator;
        this.trainingLogger = trainingLogger;
    }

    @Override
    public String route(OrderRoutingContext context) {
        RoutingFeatures features = metricsCollector.collectAll();

        String targetQueue =
                targetGenerator.chooseTarget(features);

        trainingLogger.log(features, targetQueue);

        return targetQueue;
    }
}