package ict.um.orders.ml.training;

import ict.um.orders.ml.features.QueueFeatures;
import ict.um.orders.ml.features.RoutingFeatures;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

@Component
@ConditionalOnProperty(
        name = "routing.strategy",
        havingValue = "training"
)
public class TrainingDataLogger {

    private final FileWriter writer;

    public TrainingDataLogger(
            @Value("${training.data-path:training-data.csv}") String path
    ) throws IOException {
        File file = new File(path);

        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }

        boolean newFile = !file.exists() || file.length() == 0;
        this.writer = new FileWriter(file, true);

        if (newFile) {
            writer.write(
                    "high_queue_length,"
                            + "high_consumer_throughput,"
                            + "high_arrival_interval,"
                            + "high_utilisation,"
                            + "high_backlog_growth,"
                            + "high_tail_latency,"
                            + "medium_queue_length,"
                            + "medium_consumer_throughput,"
                            + "medium_arrival_interval,"
                            + "medium_utilisation,"
                            + "medium_backlog_growth,"
                            + "medium_tail_latency,"
                            + "low_queue_length,"
                            + "low_consumer_throughput,"
                            + "low_arrival_interval,"
                            + "low_utilisation,"
                            + "low_backlog_growth,"
                            + "low_tail_latency,"
                            + "target_queue"
                            + System.lineSeparator()
            );

            writer.flush();
        }
    }

    public synchronized void log(
            RoutingFeatures features,
            String chosenRoute
    ) {
        try {
            StringBuilder row = new StringBuilder();

            for (String queueName : RoutingFeatures.QUEUE_ORDER) {
                QueueFeatures queue = features.queues().get(queueName);

                if (queue == null) {
                    throw new IllegalStateException(
                            "Missing features for queue: " + queueName
                    );
                }

                for (double value : queue.toVector()) {
                    row.append(value).append(',');
                }
            }

            row.append(chosenRoute)
                    .append(System.lineSeparator());

            writer.write(row.toString());
            writer.flush();

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to write routing training data",
                    exception
            );
        }
    }

    @PreDestroy
    public void close() {
        try {
            writer.close();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to close routing training-data file",
                    exception
            );
        }
    }
}