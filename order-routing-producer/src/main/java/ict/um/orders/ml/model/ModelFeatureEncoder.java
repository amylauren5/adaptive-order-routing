package ict.um.orders.ml.model;

import ict.um.orders.ml.features.QueueFeatures;
import ict.um.orders.ml.features.RoutingCandidate;

import java.util.List;
import java.util.Objects;

public class ModelFeatureEncoder {

    private static final String ORDER_STATUS_PREFIX = "order_status_";
    private static final String CATEGORY_PREFIX = "category_";
    private static final String SELECTED_QUEUE_PREFIX = "selected_queue_";

    private final List<String> transformedFeatureNames;

    public ModelFeatureEncoder(ModelSchema schema) {
        Objects.requireNonNull(schema, "schema must not be null");

        if (schema.transformedFeatureNames() == null
                || schema.transformedFeatureNames().isEmpty()) {
            throw new IllegalArgumentException(
                    "Model schema contains no transformed feature names"
            );
        }

        this.transformedFeatureNames =
                List.copyOf(schema.transformedFeatureNames());
    }

    public float[] encode(RoutingCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate must not be null");
        Objects.requireNonNull(
                candidate.context(),
                "candidate context must not be null"
        );
        Objects.requireNonNull(
                candidate.routingFeatures(),
                "routing features must not be null"
        );

        float[] encoded =
                new float[transformedFeatureNames.size()];

        for (int index = 0;
             index < transformedFeatureNames.size();
             index++) {

            String featureName =
                    transformedFeatureNames.get(index);

            encoded[index] =
                    encodeFeature(featureName, candidate);
        }

        return encoded;
    }

    public int featureCount() {
        return transformedFeatureNames.size();
    }

    private float encodeFeature(
            String featureName,
            RoutingCandidate candidate
    ) {
        if (featureName.startsWith(ORDER_STATUS_PREFIX)) {
            return encodeOrderStatus(featureName, candidate);
        }

        if (featureName.startsWith(CATEGORY_PREFIX)) {
            return encodeCategory(featureName, candidate);
        }

        if (featureName.startsWith(SELECTED_QUEUE_PREFIX)) {
            return encodeSelectedQueue(featureName, candidate);
        }

        return encodeNumericFeature(featureName, candidate);
    }

    private float encodeOrderStatus(
            String featureName,
            RoutingCandidate candidate
    ) {
        String expectedStatus = featureName.substring(
                ORDER_STATUS_PREFIX.length()
        );

        String actualStatus =
                candidate.context().status().name();

        return oneHot(actualStatus.equals(expectedStatus));
    }

    private float encodeCategory(
            String featureName,
            RoutingCandidate candidate
    ) {
        String expectedCategory = featureName.substring(
                CATEGORY_PREFIX.length()
        );

        String actualCategory =
                candidate.context().category();

        return oneHot(
                expectedCategory.equals(actualCategory)
        );
    }

    private float encodeSelectedQueue(
            String featureName,
            RoutingCandidate candidate
    ) {
        String expectedQueue = featureName.substring(
                SELECTED_QUEUE_PREFIX.length()
        );

        return oneHot(
                expectedQueue.equals(candidate.selectedQueue())
        );
    }

    private float encodeNumericFeature(
            String featureName,
            RoutingCandidate candidate
    ) {
        return switch (featureName) {
            case "order_value" ->
                    finiteFloat(
                            candidate.context().orderValue(),
                            featureName
                    );

            case "item_count" ->
                    finiteFloat(
                            candidate.context().itemCount(),
                            featureName
                    );

            case "queue1_length" ->
                    queueValue(
                            candidate,
                            "queue1",
                            featureName,
                            QueueFeatures::queueLength
                    );

            case "queue1_consumer_throughput" ->
                    queueValue(
                            candidate,
                            "queue1",
                            featureName,
                            QueueFeatures::consumerThroughput
                    );

            case "queue1_arrival_interval" ->
                    queueValue(
                            candidate,
                            "queue1",
                            featureName,
                            QueueFeatures::arrivalInterval
                    );

            case "queue1_utilisation" ->
                    queueValue(
                            candidate,
                            "queue1",
                            featureName,
                            QueueFeatures::utilisation
                    );

            case "queue1_backlog_growth" ->
                    queueValue(
                            candidate,
                            "queue1",
                            featureName,
                            QueueFeatures::backlogGrowth
                    );

            case "queue1_estimated_delay" ->
                    queueValue(
                            candidate,
                            "queue1",
                            featureName,
                            QueueFeatures::estimatedDelay
                    );

            case "queue2_length" ->
                    queueValue(
                            candidate,
                            "queue2",
                            featureName,
                            QueueFeatures::queueLength
                    );

            case "queue2_consumer_throughput" ->
                    queueValue(
                            candidate,
                            "queue2",
                            featureName,
                            QueueFeatures::consumerThroughput
                    );

            case "queue2_arrival_interval" ->
                    queueValue(
                            candidate,
                            "queue2",
                            featureName,
                            QueueFeatures::arrivalInterval
                    );

            case "queue2_utilisation" ->
                    queueValue(
                            candidate,
                            "queue2",
                            featureName,
                            QueueFeatures::utilisation
                    );

            case "queue2_backlog_growth" ->
                    queueValue(
                            candidate,
                            "queue2",
                            featureName,
                            QueueFeatures::backlogGrowth
                    );

            case "queue2_estimated_delay" ->
                    queueValue(
                            candidate,
                            "queue2",
                            featureName,
                            QueueFeatures::estimatedDelay
                    );

            case "queue3_length" ->
                    queueValue(
                            candidate,
                            "queue3",
                            featureName,
                            QueueFeatures::queueLength
                    );

            case "queue3_consumer_throughput" ->
                    queueValue(
                            candidate,
                            "queue3",
                            featureName,
                            QueueFeatures::consumerThroughput
                    );

            case "queue3_arrival_interval" ->
                    queueValue(
                            candidate,
                            "queue3",
                            featureName,
                            QueueFeatures::arrivalInterval
                    );

            case "queue3_utilisation" ->
                    queueValue(
                            candidate,
                            "queue3",
                            featureName,
                            QueueFeatures::utilisation
                    );

            case "queue3_backlog_growth" ->
                    queueValue(
                            candidate,
                            "queue3",
                            featureName,
                            QueueFeatures::backlogGrowth
                    );

            case "queue3_estimated_delay" ->
                    queueValue(
                            candidate,
                            "queue3",
                            featureName,
                            QueueFeatures::estimatedDelay
                    );

            default -> throw new IllegalArgumentException(
                    "Unsupported model feature: " + featureName
            );
        };
    }

    private float queueValue(
            RoutingCandidate candidate,
            String queueName,
            String featureName,
            QueueMetricExtractor extractor
    ) {
        QueueFeatures queueFeatures =
                candidate.routingFeatures()
                        .queues()
                        .get(queueName);

        if (queueFeatures == null) {
            throw new IllegalArgumentException(
                    "Missing metrics for queue: " + queueName
            );
        }

        return finiteFloat(
                extractor.extract(queueFeatures),
                featureName
        );
    }

    private float finiteFloat(
            double value,
            String featureName
    ) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    "Feature "
                            + featureName
                            + " contains non-finite value: "
                            + value
            );
        }

        return (float) value;
    }

    private float oneHot(boolean matches) {
        return matches ? 1.0F : 0.0F;
    }

    @FunctionalInterface
    private interface QueueMetricExtractor {

        double extract(QueueFeatures features);
    }
}