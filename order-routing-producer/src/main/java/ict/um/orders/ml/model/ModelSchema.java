package ict.um.orders.ml.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ModelSchema(
        String target,

        @JsonProperty("categorical_columns")
        List<String> categoricalColumns,

        @JsonProperty("numeric_columns")
        List<String> numericColumns,

        @JsonProperty("categorical_values_in_training_order")
        Map<String, List<String>> categoricalValuesInTrainingOrder,

        @JsonProperty("transformed_feature_names")
        List<String> transformedFeatureNames
) {
}