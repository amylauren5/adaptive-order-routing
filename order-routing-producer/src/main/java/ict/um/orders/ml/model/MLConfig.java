package ict.um.orders.ml.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;

@Configuration
@ConditionalOnProperty(
        name = "routing.strategy",
        havingValue = "ml"
)
public class MLConfig {

    @Bean
    public ModelSchema modelSchema(
            @Value("${ml.schema-path}") Resource schemaResource,
            ObjectMapper objectMapper
    ) {
        if (!schemaResource.exists()) {
            throw new IllegalStateException(
                    "ML model schema not found: " + schemaResource
            );
        }

        try (InputStream inputStream =
                     schemaResource.getInputStream()) {

            return objectMapper.readValue(
                    inputStream,
                    ModelSchema.class
            );

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to load ML model schema: "
                            + schemaResource,
                    exception
            );
        }
    }

    @Bean
    public ModelFeatureEncoder modelFeatureEncoder(
            ModelSchema modelSchema
    ) {
        return new ModelFeatureEncoder(modelSchema);
    }

    @Bean
    public WorkloadPredictionModel workloadPredictionModel(
            @Value("${ml.model-path}") Resource modelResource,
            ModelFeatureEncoder encoder
    ) {
        return new WorkloadPredictionModel(
                ModelLoader.loadXGBoost(modelResource),
                encoder
        );
    }
}