package ict.um.orders.evaluation;

import ict.um.orders.core_api.config.QueueNames;
import ict.um.orders.ml.features.QueueFeatures;
import ict.um.orders.ml.features.RoutingFeatures;
import ict.um.orders.workload.WorkloadGenerator;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
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
public class QueueMetricsLogger {

    private final RoutingMetricsCollector metricsCollector;
    private final WorkloadGenerator workloadGenerator;
    private final BufferedWriter writer;
    private final String runId;

    public QueueMetricsLogger(
            RoutingMetricsCollector metricsCollector,
            WorkloadGenerator workloadGenerator,
            @Value("${experiment.data-directory}") String dataDirectory,
            @Value("${experiment.run-id}") String runId
    ) throws IOException {

        validateRunId(runId);

        this.metricsCollector = metricsCollector;
        this.workloadGenerator = workloadGenerator;
        this.runId = runId;

        Path runDirectory =
                Path.of(dataDirectory, runId);

        Files.createDirectories(runDirectory);

        Path outputPath =
                runDirectory.resolve("queue_metrics.csv");

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
                        + "timestamp,"
                        + "elapsed_ms,"
                        + "phase,"
                        + "q1_length,"
                        + "q1_arrival_rate,"
                        + "q1_consumer_throughput,"
                        + "q1_utilisation,"
                        + "q1_backlog_growth,"
                        + "q2_length,"
                        + "q2_arrival_rate,"
                        + "q2_consumer_throughput,"
                        + "q2_utilisation,"
                        + "q2_backlog_growth,"
                        + "q3_length,"
                        + "q3_arrival_rate,"
                        + "q3_consumer_throughput,"
                        + "q3_utilisation,"
                        + "q3_backlog_growth,"
                        + "aggregate_backlog"
        );

        writer.newLine();
        writer.flush();
    }

    @Scheduled(
            fixedDelayString =
                    "${evaluation.queue-sampling-interval-ms:1000}"
    )
    public synchronized void sample() {
        RoutingFeatures features =
                metricsCollector.collectAll();

        QueueFeatures q1 =
                features.queues().get(QueueNames.QUEUE_1);

        QueueFeatures q2 =
                features.queues().get(QueueNames.QUEUE_2);

        QueueFeatures q3 =
                features.queues().get(QueueNames.QUEUE_3);

        if (q1 == null || q2 == null || q3 == null) {
            throw new IllegalStateException(
                    "Missing queue features during evaluation sampling"
            );
        }

        long timestamp =
                System.currentTimeMillis();

        long workloadStartedAt =
                workloadGenerator.getWorkloadStartedAt();

        if (workloadStartedAt <= 0L) {
            return;
        }

        long elapsedMs =
                timestamp - workloadStartedAt;

        String phase =
                workloadGenerator.getCurrentPhaseName();

        double aggregateBacklog =
                q1.queueLength()
                        + q2.queueLength()
                        + q3.queueLength();

        try {
            writer.write(
                    runId + ","
                            + timestamp + ","
                            + elapsedMs + ","
                            + phase + ","
                            + q1.queueLength() + ","
                            + q1.arrivalRate() + ","
                            + q1.consumerThroughput() + ","
                            + q1.utilisation() + ","
                            + q1.backlogGrowth() + ","
                            + q2.queueLength() + ","
                            + q2.arrivalRate() + ","
                            + q2.consumerThroughput() + ","
                            + q2.utilisation() + ","
                            + q2.backlogGrowth() + ","
                            + q3.queueLength() + ","
                            + q3.arrivalRate() + ","
                            + q3.consumerThroughput() + ","
                            + q3.utilisation() + ","
                            + q3.backlogGrowth() + ","
                            + aggregateBacklog
            );

            writer.newLine();
            writer.flush();

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to write queue evaluation metrics",
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
                    "Failed to close queue metrics file",
                    exception
            );
        }
    }
}
