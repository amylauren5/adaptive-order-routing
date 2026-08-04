package ict.um.orders.training;

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
public class TrainingOutcomeLogger {

    private final BufferedWriter writer;

    public TrainingOutcomeLogger(
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
                "routing-decision-outcomes.csv"
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
                "routing_decision_id,"
                        + "selected_queue,"
                        + "published_at,"
                        + "consumer_started_at,"
                        + "realised_waiting_time_ms"
        );
        writer.newLine();
        writer.flush();
    }

    public synchronized void logOutcome(
            String routingDecisionId,
            String selectedQueue,
            long publishedAt,
            long consumerStartedAt
    ) {
        if (routingDecisionId == null
                || routingDecisionId.isBlank()) {
            throw new IllegalArgumentException(
                    "Routing decision ID is required"
            );
        }

        if (selectedQueue == null
                || selectedQueue.isBlank()) {
            throw new IllegalArgumentException(
                    "Selected queue is required"
            );
        }

        long realisedWaitingTimeMs =
                Math.max(0L, consumerStartedAt - publishedAt);

        try {
            writer.write(
                    routingDecisionId + ","
                            + selectedQueue + ","
                            + publishedAt + ","
                            + consumerStartedAt + ","
                            + realisedWaitingTimeMs
                            + System.lineSeparator()
            );

            writer.flush();

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to write routing outcome",
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
                    "Failed to close routing outcome file",
                    exception
            );
        }
    }
}