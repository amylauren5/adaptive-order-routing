package ict.um.orders.ml.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import ict.um.orders.core_api.enums.OrderStatus;
import ict.um.orders.ml.features.QueueFeatures;
import ict.um.orders.ml.features.RoutingCandidate;
import ict.um.orders.routing.OrderRoutingContext;
import ml.dmlc.xgboost4j.java.Booster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkloadPredictionModelParityTest {

    private ModelFeatureEncoder encoder;
    private WorkloadPredictionModel predictionModel;

    @BeforeEach
    void setUp() throws Exception {

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

        encoder =
                new ModelFeatureEncoder(
                        schema
                );

        Booster booster =
                ModelLoader.loadXGBoost(
                        modelResource
                );

        predictionModel =
                new WorkloadPredictionModel(
                        booster,
                        encoder
                );
    }

    @Test
    void shouldMatchExpectedFeatureEncoding() {

        RoutingCandidate candidate =
                createCandidate();

        float[] encoded =
                encoder.encode(
                        candidate
                );

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
                expectedEncoding.length,
                encoder.featureCount(),
                "Feature count should match the exported schema"
        );

        assertArrayEquals(
                expectedEncoding,
                encoded,
                0.000001F,
                "Java feature encoding should match "
                        + "the expected training representation"
        );
    }

    @Test
    void shouldProduceValidPredictionFromExportedModel()
            throws Exception {

        RoutingCandidate candidate =
                createCandidate();

        double prediction =
                predictionModel.predictWaitingTime(
                        candidate
                );

        assertFalse(
                Double.isNaN(
                        prediction
                ),
                "Prediction must not be NaN"
        );

        assertFalse(
                Double.isInfinite(
                        prediction
                ),
                "Prediction must be finite"
        );

        assertTrue(
                prediction >= 0.0,
                "Runtime waiting-time prediction "
                        + "must not be negative"
        );
    }

    private RoutingCandidate createCandidate() {

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

        return new RoutingCandidate(
                context,
                queueFeatures
        );
    }
}