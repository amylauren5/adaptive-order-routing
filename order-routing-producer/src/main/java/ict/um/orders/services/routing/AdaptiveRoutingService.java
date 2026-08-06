package ict.um.orders.services.routing;

import ict.um.orders.core_api.config.QueueNames;
import ict.um.orders.ml.features.RoutingCandidate;
import ict.um.orders.ml.features.RoutingFeatures;
import ict.um.orders.ml.metrics.RoutingMetricsCollector;
import ict.um.orders.ml.model.WorkloadPredictionModel;
import ict.um.orders.routing.OrderRoutingContext;
import ict.um.orders.routing.RoutingDecision;
import ml.dmlc.xgboost4j.java.XGBoostError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@ConditionalOnProperty(
        name = "routing.strategy",
        havingValue = "ml"
)
public class AdaptiveRoutingService implements RoutingService {

    private static final Logger logger =
            LoggerFactory.getLogger(AdaptiveRoutingService.class);

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
            RoutingFeatures routingFeatures =
                    metricsCollector.collectAll();

            List<QueuePrediction> predictions = List.of(
                    predictForQueue(
                            context,
                            routingFeatures,
                            QueueNames.QUEUE_1
                    ),
                    predictForQueue(
                            context,
                            routingFeatures,
                            QueueNames.QUEUE_2
                    ),
                    predictForQueue(
                            context,
                            routingFeatures,
                            QueueNames.QUEUE_3
                    )
            );

            QueuePrediction bestPrediction = predictions.stream()
                    .min(Comparator.comparingDouble(
                            QueuePrediction::predictedWaitingTimeMs
                    ))
                    .orElseThrow(() -> new IllegalStateException(
                            "No queue predictions were produced"
                    ));

            logger.info(
                    "ML selected queue: orderId={}, queue={}, predictedWaitingTimeMs={}",
                    context.orderId(),
                    bestPrediction.queue(),
                    bestPrediction.predictedWaitingTimeMs()
            );

            return new RoutingDecision(
                    UUID.randomUUID().toString(),
                    bestPrediction.queue()
            );

        } catch (Exception exception) {
            logger.error(
                    "ML routing failed for orderId={}",
                    context.orderId(),
                    exception
            );

            throw new IllegalStateException(
                    "Failed to perform ML-based routing",
                    exception
            );
        }
    }

    private QueuePrediction predictForQueue(
            OrderRoutingContext context,
            RoutingFeatures routingFeatures,
            String queue
    ) throws XGBoostError {

        RoutingCandidate candidate = new RoutingCandidate(
                context,
                queue,
                routingFeatures
        );

        double predictedWaitingTimeMs =
                predictionModel.predictWaitingTime(candidate);

        logger.info(
                "ML prediction: orderId={}, status={}, queue={}, predictedWaitingTimeMs={}",
                context.orderId(),
                context.status(),
                queue,
                predictedWaitingTimeMs
        );

        return new QueuePrediction(
                queue,
                predictedWaitingTimeMs
        );
    }

    private record QueuePrediction(
            String queue,
            double predictedWaitingTimeMs
    ) {
    }
}