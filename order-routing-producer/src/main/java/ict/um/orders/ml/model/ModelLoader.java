package ict.um.orders.ml.model;

import ml.dmlc.xgboost4j.java.Booster;
import ml.dmlc.xgboost4j.java.XGBoost;
import ml.dmlc.xgboost4j.java.XGBoostError;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;

public final class ModelLoader {

    private ModelLoader() {
    }

    public static Booster loadXGBoost(Resource resource) {
        if (!resource.exists()) {
            throw new IllegalStateException(
                    "XGBoost model not found: " + resource
            );
        }

        try (InputStream inputStream = resource.getInputStream()) {
            return XGBoost.loadModel(inputStream);

        } catch (IOException | XGBoostError exception) {
            throw new IllegalStateException(
                    "Failed to load XGBoost model: " + resource,
                    exception
            );
        }
    }
}