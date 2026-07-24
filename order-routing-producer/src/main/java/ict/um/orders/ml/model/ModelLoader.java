package ict.um.orders.ml.model;

import ml.dmlc.xgboost4j.java.Booster;
import ml.dmlc.xgboost4j.java.XGBoost;
import ml.dmlc.xgboost4j.java.XGBoostError;

public class ModelLoader {

    public static Booster loadXGBoost(String path) {
        try {
            return XGBoost.loadModel(path);

        } catch (XGBoostError e) {
            throw new RuntimeException(
                    "Failed to load XGBoost model: " + path,
                    e
            );
        }
    }
}