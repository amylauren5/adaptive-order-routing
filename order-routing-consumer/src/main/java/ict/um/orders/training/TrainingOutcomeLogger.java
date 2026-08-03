package ict.um.orders.training;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

@Component
@ConditionalOnProperty(
        name = "training.collection-enabled",
        havingValue = "true"
)
public class TrainingOutcomeLogger {

    private final FileWriter writer;

    public TrainingOutcomeLogger(
            @Value(
                    "${training.outcome-data-path:"
                            + "data/routing-decision-outcomes.csv}"
            )
            String path
    ) throws IOException {
        File file = new File(path);

        File parent = file.getParentFile();
        if (parent != null
                && !parent.exists()
                && !parent.mkdirs()) {
            throw new IOException(
                    "Failed to create outcome-data directory: "
                            + parent.getAbsolutePath()
            );
        }

        boolean newFile =
                !file.exists() || file.length() == 0L;

        writer = new FileWriter(file, true);

        if (newFile) {
            writer.write(
                    "routing_decision_id,"
                            + "selected_queue,"
                            + "published_at,"
                            + "consumer_started_at,"
                            + "realised_waiting_time_ms"
                            + System.lineSeparator()
            );
            writer.flush();
        }
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