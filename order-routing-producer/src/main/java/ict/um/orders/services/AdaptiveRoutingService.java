package ict.um.orders.services;

import ict.um.orders.ml.features.RoutingFeatures;
import ict.um.orders.ml.metrics.MetricsCollector;
import ict.um.orders.ml.model.WorkloadPredictionModel;
import ict.um.orders.ml.training.TrainingDataLogger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AdaptiveRoutingService {

    private final MetricsCollector metricsCollector;
    private final WorkloadPredictionModel predictionModel;
    private final TrainingDataLogger trainingLogger;

    @Autowired
    public AdaptiveRoutingService(MetricsCollector metricsCollector,
                          WorkloadPredictionModel predictionModel,
                          TrainingDataLogger trainingLogger) {
        this.metricsCollector = metricsCollector;
        this.predictionModel = predictionModel;
        this.trainingLogger = trainingLogger;
    }

    public String route(Object evt) {
        try {
            RoutingFeatures features = metricsCollector.collectAll();
            String queue = predictionModel.predict(features);
            trainingLogger.log(features, queue);
            return queue;
        } catch (Exception e) {
            throw new RuntimeException("Routing failed", e);
        }
    }
}