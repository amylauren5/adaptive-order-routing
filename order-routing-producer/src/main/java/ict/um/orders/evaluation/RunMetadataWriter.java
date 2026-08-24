package ict.um.orders.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConditionalOnProperty(
        name = "evaluation.collection-enabled",
        havingValue = "true"
)
public class RunMetadataWriter {

    private final ObjectMapper objectMapper;

    private final String dataDirectory;
    private final String runId;
    private final String strategy;

    private final double arrivalScale;
    private final long workloadDurationSeconds;
    private final long randomSeed;

    private final boolean burstEnabled;
    private final long burstStartSeconds;
    private final long burstDurationSeconds;
    private final double burstMultiplier;

    private final long queueSamplingIntervalMs;
    private final long queueStateRefreshIntervalMs;

    public RunMetadataWriter(
            ObjectMapper objectMapper,
            @Value("${experiment.data-directory}") String dataDirectory,
            @Value("${experiment.run-id}") String runId,
            @Value("${routing.strategy}") String strategy,
            @Value("${workload.arrival-scale}") double arrivalScale,
            @Value("${workload.duration-seconds}") long workloadDurationSeconds,
            @Value("${workload.random-seed}") long randomSeed,
            @Value("${workload.burst-enabled:false}") boolean burstEnabled,
            @Value("${workload.burst-start-seconds:20}") long burstStartSeconds,
            @Value("${workload.burst-duration-seconds:0}") long burstDurationSeconds,
            @Value("${workload.burst-multiplier:1.0}") double burstMultiplier,
            @Value("${evaluation.queue-sampling-interval-ms:1000}") long queueSamplingIntervalMs,
            @Value("${routing.queue-state-refresh-ms:1000}") long queueStateRefreshIntervalMs
    ) {
        this.objectMapper = objectMapper;
        this.dataDirectory = dataDirectory;
        this.runId = runId;
        this.strategy = strategy;
        this.arrivalScale = arrivalScale;
        this.workloadDurationSeconds = workloadDurationSeconds;
        this.randomSeed = randomSeed;
        this.burstEnabled = burstEnabled;
        this.burstStartSeconds = burstStartSeconds;
        this.burstDurationSeconds = burstDurationSeconds;
        this.burstMultiplier = burstMultiplier;
        this.queueSamplingIntervalMs = queueSamplingIntervalMs;
        this.queueStateRefreshIntervalMs = queueStateRefreshIntervalMs;
    }

    @PostConstruct
    public void writeMetadata() throws IOException {
        validateRunId(runId);

        Path runDirectory =
                Path.of(dataDirectory, runId);

        Files.createDirectories(runDirectory);

        Path outputPath =
                runDirectory.resolve("run_metadata.json");

        Map<String, Object> metadata =
                new LinkedHashMap<>();

        metadata.put("run_id", runId);
        metadata.put("strategy", strategy);
        metadata.put("random_seed", randomSeed);

        metadata.put("workload_duration_seconds",
                workloadDurationSeconds);
        metadata.put("arrival_scale",
                arrivalScale);

        metadata.put("burst_enabled",
                burstEnabled);
        metadata.put("burst_start_seconds",
                burstStartSeconds);
        metadata.put("burst_duration_seconds",
                burstDurationSeconds);
        metadata.put("burst_multiplier",
                burstMultiplier);

        metadata.put("queue_sampling_interval_ms",
                queueSamplingIntervalMs);

        metadata.put(
                "queue_state_refresh_interval_ms",
                queueStateRefreshIntervalMs
        );

        try (var writer = Files.newBufferedWriter(
                outputPath,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
        )) {
            objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValue(writer, metadata);
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
}