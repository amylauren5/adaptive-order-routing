package ict.um.orders.ml.model;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

@Configuration
@ConditionalOnProperty(
        name = "routing.strategy",
        havingValue = "ml"
)
public class MLConfig {

    @Bean
    public WorkloadPredictionModel workloadPredictionModel(
            @Value("${ml.model-path}") Resource modelResource
    ) {
        return new WorkloadPredictionModel(
                ModelLoader.loadXGBoost(modelResource)
        );
    }
}