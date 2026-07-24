package ict.um.orders.ml.training;

import ict.um.orders.ml.features.QueueFeatures;
import ict.um.orders.ml.features.RoutingFeatures;
import org.springframework.stereotype.Component;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

@Component
public class TrainingDataLogger {

    private final FileWriter writer;

    public TrainingDataLogger() throws IOException {
        this.writer = new FileWriter("training-data.csv", true);
    }

    public void log(RoutingFeatures features, String chosenRoute) throws IOException {

        StringBuilder sb = new StringBuilder();

        // Loop through queues in a stable order
        for (Map.Entry<String, QueueFeatures> entry : features.queues().entrySet()) {
            QueueFeatures q = entry.getValue();
            double[] vec = q.toVector();

            for (double v : vec) {
                sb.append(v).append(",");
            }
        }

        sb.append(chosenRoute).append("\n");

        writer.write(sb.toString());
        writer.flush();
    }
}
