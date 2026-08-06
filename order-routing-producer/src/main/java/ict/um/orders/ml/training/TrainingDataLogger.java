package ict.um.orders.ml.training;

import ict.um.orders.ml.features.QueueFeatures;
import ict.um.orders.ml.features.RoutingFeatures;
import ict.um.orders.routing.OrderRoutingContext;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.io.IOException;

@Component
@ConditionalOnProperty(
        name = "training.collection-enabled",
        havingValue = "true"
)
public class TrainingDataLogger {

    private final BufferedWriter writer;

    public TrainingDataLogger(
            @Value("${experiment.data-directory}") String dataDirectory,
            @Value("${experiment.run-id}") String runId
    ) throws IOException {

        validateRunId(runId);

        Path runDirectory = Path.of(
                dataDirectory,
                runId
        );

        Files.createDirectories(runDirectory);

        Path outputPath = runDirectory.resolve(
                "pending-routing-observations.csv"
        );

        this.writer = Files.newBufferedWriter(
                outputPath,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
        );

        writeHeader();
    }

    private void validateRunId(String runId) {
        if (runId == null
                || !runId.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException(
                    "Invalid experiment run ID: " + runId
            );
        }
    }

    public synchronized void logPending(
            String routingDecisionId,
            OrderRoutingContext context,
            RoutingFeatures features,
            String selectedQueue
    ) {
        validate(
                routingDecisionId,
                context,
                features,
                selectedQueue
        );

        try {
            StringBuilder row = new StringBuilder();

            append(row, routingDecisionId);
            append(row, context.orderId());
            append(row, context.status().name());
            append(row, context.category());
            append(row, context.orderValue());
            append(row, context.itemCount());
            append(row, context.timestamp());
            append(row, selectedQueue);

            for (String queueKey : RoutingFeatures.QUEUE_ORDER) {
                QueueFeatures queueFeatures =
                        features.queues().get(queueKey);

                if (queueFeatures == null) {
                    throw new IllegalStateException(
                            "Missing features for queue: " + queueKey
                    );
                }

                append(row, queueFeatures.queueLength());
                append(row, queueFeatures.consumerThroughput());
                append(row, queueFeatures.arrivalInterval());
                append(row, queueFeatures.utilisation());
                append(row, queueFeatures.backlogGrowth());
                append(row, queueFeatures.estimatedDelay());
            }

            removeTrailingComma(row);
            row.append(System.lineSeparator());

            writer.write(row.toString());
            writer.flush();

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to write pending routing observation",
                    exception
            );
        }
    }

    private void writeHeader() throws IOException {
        writer.write(
                "routing_decision_id,"
                        + "order_id,"
                        + "order_status,"
                        + "category,"
                        + "order_value,"
                        + "item_count,"
                        + "event_timestamp,"
                        + "selected_queue,"
                        + queueHeaders("queue1")
                        + queueHeaders("queue2")
                        + queueHeaders("queue3")
        );

        writer.write(System.lineSeparator());
        writer.flush();
    }

    private String queueHeaders(String queueName) {
        return queueName + "_length,"
                + queueName + "_consumer_throughput,"
                + queueName + "_arrival_interval,"
                + queueName + "_utilisation,"
                + queueName + "_backlog_growth,"
                + queueName + "_estimated_delay"
                + ("queue3".equals(queueName) ? "" : ",");
    }

    private void validate(
            String routingDecisionId,
            OrderRoutingContext context,
            RoutingFeatures features,
            String selectedQueue
    ) {
        if (routingDecisionId == null
                || routingDecisionId.isBlank()) {
            throw new IllegalArgumentException(
                    "Routing decision ID is required"
            );
        }

        if (context == null) {
            throw new IllegalArgumentException(
                    "Order routing context is required"
            );
        }

        if (features == null || features.queues() == null) {
            throw new IllegalArgumentException(
                    "Routing features are required"
            );
        }

        if (selectedQueue == null || selectedQueue.isBlank()) {
            throw new IllegalArgumentException(
                    "Selected queue is required"
            );
        }
    }

    private void append(
            StringBuilder row,
            Object value
    ) {
        row.append(escapeCsv(value))
                .append(',');
    }

    private String escapeCsv(Object value) {
        if (value == null) {
            return "";
        }

        String text = value.toString();

        if (text.contains(",")
                || text.contains("\"")
                || text.contains("\n")
                || text.contains("\r")) {
            return "\""
                    + text.replace("\"", "\"\"")
                    + "\"";
        }

        return text;
    }

    private void removeTrailingComma(StringBuilder row) {
        if (!row.isEmpty()
                && row.charAt(row.length() - 1) == ',') {
            row.deleteCharAt(row.length() - 1);
        }
    }

    @PreDestroy
    public synchronized void close() {
        try {
            writer.close();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to close pending training-data file",
                    exception
            );
        }
    }
}