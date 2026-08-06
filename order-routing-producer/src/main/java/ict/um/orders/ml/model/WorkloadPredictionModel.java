package ict.um.orders.ml.model;

import ict.um.orders.ml.features.RoutingCandidate;
import ml.dmlc.xgboost4j.java.Booster;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoostError;

import java.util.Objects;

public class WorkloadPredictionModel {

    private final Booster booster;
    private final ModelFeatureEncoder featureEncoder;

    public WorkloadPredictionModel(
            Booster booster,
            ModelFeatureEncoder featureEncoder
    ) {
        this.booster = Objects.requireNonNull(
                booster,
                "booster must not be null"
        );
        this.featureEncoder = Objects.requireNonNull(
                featureEncoder,
                "featureEncoder must not be null"
        );
    }

    public double predictWaitingTime(
            RoutingCandidate candidate
    ) throws XGBoostError {

        float[] values = featureEncoder.encode(candidate);

        DMatrix matrix = new DMatrix(
                values,
                1,
                values.length,
                Float.NaN
        );

        try {
            float[][] predictions = booster.predict(matrix);

            if (predictions.length != 1
                    || predictions[0].length != 1) {
                throw new IllegalStateException(
                        "Expected exactly one regression prediction, "
                                + "but received "
                                + predictions.length
                                + " prediction row(s)"
                );
            }

            double predictedWaitingTimeMs =
                    predictions[0][0];

            if (!Double.isFinite(predictedWaitingTimeMs)) {
                throw new IllegalStateException(
                        "XGBoost returned a non-finite prediction: "
                                + predictedWaitingTimeMs
                );
            }

            return Math.max(0.0, predictedWaitingTimeMs);

        } finally {
            matrix.dispose();
        }
    }
}