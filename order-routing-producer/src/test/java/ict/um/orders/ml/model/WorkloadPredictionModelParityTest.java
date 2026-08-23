package ict.um.orders.ml.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import ict.um.orders.core_api.enums.OrderStatus;
import ict.um.orders.ml.features.QueueFeatures;
import ict.um.orders.ml.features.RoutingCandidate;
import ict.um.orders.routing.OrderRoutingContext;
import ml.dmlc.xgboost4j.java.Booster;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkloadPredictionModelParityTest {

    private static final double EXPECTED_PREDICTION_MS =
            20115.626953125;

    @Test
    void shouldMatchPythonFeatureEncodingAndPrediction()
            throws Exception {

        ClassPathResource schemaResource =
                new ClassPathResource(
                        "models/model-schema.json"
                );

        ClassPathResource modelResource =
                new ClassPathResource(
                        "models/xgboost-model.json"
                );

        ObjectMapper objectMapper =
                new ObjectMapper();

        ModelSchema schema;

        try (InputStream inputStream =
                     schemaResource.getInputStream()) {

            schema = objectMapper.readValue(
                    inputStream,
                    ModelSchema.class
            );
        }

        ModelFeatureEncoder encoder =
                new ModelFeatureEncoder(schema);

        Booster booster =
                ModelLoader.loadXGBoost(
                        modelResource
                );

        WorkloadPredictionModel predictionModel =
                new WorkloadPredictionModel(
                        booster,
                        encoder
                );

        OrderRoutingContext context =
                new OrderRoutingContext(
                        "parity-test-order",
                        OrderStatus.DISPATCHED,
                        "auto",
                        98.0,
                        1,
                        0L
                );

        QueueFeatures queueFeatures =
                new QueueFeatures(
                        68.0,
                        0.6,
                        0.2,
                        3.0,
                        0.0
                );

        RoutingCandidate candidate =
                new RoutingCandidate(
                        context,
                        queueFeatures
                );

        float[] encoded =
                encoder.encode(candidate);

        float[] expectedEncoding = {
                // order_status
                0.0F, // APPROVED
                0.0F, // CANCELLED
                0.0F, // COMPLETED
                0.0F, // CREATED
                1.0F, // DISPATCHED

                // category
                1.0F, // auto
                0.0F, // bed_bath_table
                0.0F, // computers_accessories
                0.0F, // furniture_decor
                0.0F, // garden_tools
                0.0F, // health_beauty
                0.0F, // housewares
                0.0F, // other
                0.0F, // sports_leisure
                0.0F, // telephony
                0.0F, // watches_gifts

                // numeric
                98.0F,
                1.0F,
                68.0F,
                0.6F,
                0.2F,
                3.0F,
                0.0F
        };

        assertEquals(
                23,
                encoder.featureCount()
        );

        assertArrayEquals(
                expectedEncoding,
                encoded,
                0.000001F
        );

        double javaPrediction =
                predictionModel.predictWaitingTime(
                        candidate
                );

        assertEquals(
                EXPECTED_PREDICTION_MS,
                javaPrediction,
                1.0,
                "Java prediction should match Python "
                        + "within 1 ms"
        );
    }
}