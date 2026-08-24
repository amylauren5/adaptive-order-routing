package ict.um.orders.evaluation;

import ict.um.orders.core_api.messaging.RoutedEventMessage;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@Component
@ConditionalOnProperty(
        name = "evaluation.collection-enabled",
        havingValue = "true"
)
public class EventMetricsLogger {

    private final BufferedWriter writer;
    private final String runId;

    public EventMetricsLogger(
            @Value("${experiment.data-directory}") String dataDirectory,
            @Value("${experiment.run-id}") String runId
    ) throws IOException {

        validateRunId(runId);

        this.runId = runId;

        Path runDirectory = Path.of(
                dataDirectory,
                runId
        );

        Files.createDirectories(runDirectory);

        Path outputPath = runDirectory.resolve(
                "event_metrics.csv"
        );

        this.writer = Files.newBufferedWriter(
                outputPath,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
        );

        writeHeader();
    }

    private void writeHeader() throws IOException {
        writer.write(
                "run_id,"
                        + "routing_decision_id,"
                        + "event_type,"
                        + "selected_queue,"
                        + "published_at,"
                        + "processing_started_at,"
                        + "consumer_completed_at,"
                        + "queueing_latency_ms,"
                        + "processing_time_ms"
        );

        writer.newLine();
        writer.flush();
    }

    public synchronized void logEvent(
            RoutedEventMessage routedMessage,
            long processingStartedAt,
            long consumerCompletedAt
    ) {
        if (routedMessage == null) {
            throw new IllegalArgumentException(
                    "Routed event message is required"
            );
        }

        long publishedAt = routedMessage.getPublishedAt();

        long queueingLatencyMs =
                Math.max(
                        0L,
                        processingStartedAt - publishedAt
                );

        long processingTimeMs =
                Math.max(
                        0L,
                        consumerCompletedAt - processingStartedAt
                );

        try {
            writer.write(
                    runId + ","
                            + routedMessage.getRoutingDecisionId() + ","
                            + routedMessage.getEventType() + ","
                            + routedMessage.getSelectedQueue() + ","
                            + publishedAt + ","
                            + processingStartedAt + ","
                            + consumerCompletedAt + ","
                            + queueingLatencyMs + ","
                            + processingTimeMs
            );

            writer.newLine();
            writer.flush();

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to write event evaluation metric",
                    exception
            );
        }
    }

    private void validateRunId(String runId) {
        if (runId == null
                || !runId.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException(
                    "Invalid experiment run ID: " + runId
            );
        }
    }

    @PreDestroy
    public synchronized void close() {
        try {
            writer.close();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to close event metrics file",
                    exception
            );
        }
    }
}