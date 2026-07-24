package ict.um.orders.ml.model;

import ict.um.orders.ml.features.QueueFeatures;
import ml.dmlc.xgboost4j.java.Booster;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoostError;
import org.springframework.stereotype.Component;

@Component
public class WorkloadPredictionModel {

    private final Booster booster;

    public WorkloadPredictionModel(String modelPath) {
        this.booster = ModelLoader.loadXGBoost(modelPath);
    }

    public double predict(QueueFeatures features)
            throws XGBoostError {

        float[] values = toFloatArray(features.toVector());

        DMatrix matrix = new DMatrix(
                values,
                1,
                values.length,
                Float.NaN
        );

        float[][] prediction = booster.predict(matrix);

        return prediction[0][0];
    }

    private float[] toFloatArray(double[] values) {

        float[] result = new float[values.length];

        for (int i = 0; i < values.length; i++) {
            result[i] = (float) values[i];
        }

        return result;
    }
}