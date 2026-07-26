package ict.um.orders.ml.model;

import ict.um.orders.ml.features.RoutingFeatures;
import ml.dmlc.xgboost4j.java.Booster;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoostError;

public class WorkloadPredictionModel {

    private final Booster booster;

    public WorkloadPredictionModel(String modelPath) {
        this.booster = ModelLoader.loadXGBoost(modelPath);
    }

    // Helper function to map numeric values to categorical queue names
    private String mapPrediction(double p) {
        int cls = (int) p;
        return switch (cls) {
            case 0 -> "priority.low";
            case 1 -> "priority.medium";
            case 2 -> "priority.high";
            default -> throw new IllegalStateException("Unknown class: " + cls);
        };
    }

    public String predict(RoutingFeatures features) throws XGBoostError {

        float[] values = toFloatArray(features.toVector());

        DMatrix matrix = new DMatrix(
                values,
                1,
                values.length,
                Float.NaN
        );

        float[][] prediction = booster.predict(matrix);
        double raw = prediction[0][0];

        return mapPrediction(raw);
    }

    private float[] toFloatArray(double[] values) {
        float[] result = new float[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (float) values[i];
        }
        return result;
    }
}