package ict.um.orders.ml.model;

import ict.um.orders.ml.features.RoutingFeatures;
import ml.dmlc.xgboost4j.java.Booster;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoostError;

public class WorkloadPredictionModel {

    private static final int EXPECTED_FEATURE_COUNT = 18;

    private final Booster booster;

    public WorkloadPredictionModel(Booster booster) {
        this.booster = booster;
    }

    public String predict(RoutingFeatures features) throws XGBoostError {
        double[] vector = features.toVector();

        if (vector.length != EXPECTED_FEATURE_COUNT) {
            throw new IllegalArgumentException(
                    "Expected " + EXPECTED_FEATURE_COUNT
                            + " features but received "
                            + vector.length
            );
        }

        float[] values = toFloatArray(vector);

        DMatrix matrix = new DMatrix(
                values,
                1,
                values.length,
                Float.NaN
        );

        float[][] predictions = booster.predict(matrix);

        if (predictions.length == 0
                || predictions[0].length == 0) {
            throw new IllegalStateException(
                    "XGBoost returned no prediction"
            );
        }

        return mapPrediction(predictions[0]);
    }

    private float[] toFloatArray(double[] values) {
        float[] result = new float[values.length];

        for (int i = 0; i < values.length; i++) {
            if (!Double.isFinite(values[i])) {
                throw new IllegalArgumentException(
                        "Non-finite feature at index " + i
                );
            }

            result[i] = (float) values[i];
        }

        return result;
    }

    private String mapPrediction(float[] prediction) {
        if (prediction.length == 1) {
            return mapClass(Math.round(prediction[0]));
        }

        int bestClass = 0;

        for (int i = 1; i < prediction.length; i++) {
            if (prediction[i] > prediction[bestClass]) {
                bestClass = i;
            }
        }

        return mapClass(bestClass);
    }

    private String mapClass(int predictedClass) {
        return switch (predictedClass) {
            case 0 -> "priority.low";
            case 1 -> "priority.medium";
            case 2 -> "priority.high";
            default -> throw new IllegalStateException(
                    "Unknown predicted class: " + predictedClass
            );
        };
    }
}