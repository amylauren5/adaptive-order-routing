package ict.um.orders.ml.model;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MLConfig {

    @Value("${ml.model-path}")
    private String modelPath;

    @Bean
    public WorkloadPredictionModel workloadPredictionModel() {
        return new WorkloadPredictionModel(modelPath);
    }
}

