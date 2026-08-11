package ict.um.orders.evaluation;

import ict.um.orders.routing.RoutingDecision;
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
public class RoutingMetricsLogger {

    private final BufferedWriter writer;
    private final String runId;
    private final String strategy;

    public RoutingMetricsLogger(
            @Value("${experiment.data-directory}") String dataDirectory,
            @Value("${experiment.run-id}") String runId,
            @Value("${routing.strategy}") String strategy
    ) throws IOException {

        validateRunId(runId);

        this.runId = runId;
        this.strategy = strategy;

        Path runDirectory =
                Path.of(dataDirectory, runId);

        Files.createDirectories(runDirectory);

        Path outputPath =
                runDirectory.resolve("routing_metrics.csv");

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
                        + "strategy,"
                        + "routing_decision_id,"
                        + "timestamp,"
                        + "event_type,"
                        + "selected_queue,"
                        + "routing_overhead_ns"
        );

        writer.newLine();
        writer.flush();
    }

    public synchronized void logRoutingDecision(
            RoutingDecision decision,
            String eventType,
            long timestamp,
            long routingOverheadNs
    ) {
        if (decision == null) {
            throw new IllegalArgumentException(
                    "Routing decision is required"
            );
        }

        if (routingOverheadNs < 0L) {
            throw new IllegalArgumentException(
                    "Routing overhead must not be negative"
            );
        }

        try {
            writer.write(
                    runId + ","
                            + strategy + ","
                            + decision.routingDecisionId() + ","
                            + timestamp + ","
                            + eventType + ","
                            + decision.selectedQueue() + ","
                            + routingOverheadNs
            );

            writer.newLine();
            writer.flush();

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to write routing evaluation metric",
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
                    "Failed to close routing metrics file",
                    exception
            );
        }
    }
}