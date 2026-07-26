package ict.um.orders.services.routing;

import ict.um.orders.ml.features.RoutingFeatures;
import ict.um.orders.ml.metrics.RoutingMetricsCollector;
import ict.um.orders.ml.model.WorkloadPredictionModel;
import ict.um.orders.routing.OrderRoutingContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

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
    public String route(OrderRoutingContext context) {
        try {
            RoutingFeatures features = metricsCollector.collectAll();
            return predictionModel.predict(features);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to perform ML-based routing",
                    e
            );
        }
    }
}
