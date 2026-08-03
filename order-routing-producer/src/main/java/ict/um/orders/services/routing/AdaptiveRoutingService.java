package ict.um.orders.services.routing;

import ict.um.orders.ml.features.RoutingFeatures;
import ict.um.orders.ml.metrics.RoutingMetricsCollector;
import ict.um.orders.ml.model.WorkloadPredictionModel;
import ict.um.orders.routing.OrderRoutingContext;
import ict.um.orders.routing.RoutingDecision;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@ConditionalOnProperty(
        name = "routing.strategy",
        havingValue = "ml"
)
public class AdaptiveRoutingService implements RoutingService {

    private final RoutingMetricsCollector metricsCollector;
    private final WorkloadPredictionModel predictionModel;

    public AdaptiveRoutingService(
            RoutingMetricsCollector metricsCollector,
            WorkloadPredictionModel predictionModel
    ) {
        this.metricsCollector = metricsCollector;
        this.predictionModel = predictionModel;
    }

    @Override
    public RoutingDecision route(OrderRoutingContext context) {
        try {
            RoutingFeatures features =
                    metricsCollector.collectAll();

            String selectedQueue =
                    predictionModel.predict(features);

            return new RoutingDecision(
                    UUID.randomUUID().toString(),
                    selectedQueue
            );

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to perform ML-based routing",
                    exception
            );
        }
    }
}