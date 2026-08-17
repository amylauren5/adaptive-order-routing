package ict.um.orders.ml.model;

import ict.um.orders.ml.features.QueueFeatures;
import ict.um.orders.ml.features.RoutingCandidate;

import java.util.List;
import java.util.Objects;

public class ModelFeatureEncoder {

    private static final String ORDER_STATUS_PREFIX = "order_status_";
    private static final String CATEGORY_PREFIX = "category_";

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
                candidate.queueFeatures(),
                "candidate queue features must not be null"
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

    private float encodeNumericFeature(
            String featureName,
            RoutingCandidate candidate
    ) {
        QueueFeatures queue =
                candidate.queueFeatures();

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

            case "candidate_queue_length" ->
                    finiteFloat(
                            queue.queueLength(),
                            featureName
                    );

            case "candidate_consumer_throughput" ->
                    finiteFloat(
                            queue.consumerThroughput(),
                            featureName
                    );

            case "candidate_arrival_rate" ->
                    finiteFloat(
                            queue.arrivalRate(),
                            featureName
                    );

            case "candidate_utilisation" ->
                    finiteFloat(
                            queue.utilisation(),
                            featureName
                    );

            case "candidate_backlog_growth" ->
                    finiteFloat(
                            queue.backlogGrowth(),
                            featureName
                    );

            default ->
                    throw new IllegalArgumentException(
                            "Unsupported model feature: "
                                    + featureName
                    );
        };
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
}