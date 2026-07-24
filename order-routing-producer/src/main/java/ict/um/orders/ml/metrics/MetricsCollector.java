package ict.um.orders.ml.metrics;

import ict.um.orders.ml.features.QueueFeatures;
import ict.um.orders.ml.features.RoutingFeatures;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class MetricsCollector {

    private final RestClient client;
    private final String user;
    private final String pass;

    public MetricsCollector(
            @Value("${rabbit.mgmt.host}") String host,
            @Value("${rabbit.mgmt.user}") String user,
            @Value("${rabbit.mgmt.pass}") String pass
    ) {
        this.user = user;
        this.pass = pass;

        this.client = RestClient.builder()
                .baseUrl(host + "/api")
                .build();
    }

    public QueueFeatures collect(String queueName) {

        QueueInfo info = client.get()
                .uri("/queues/%2F/" + queueName)
                .headers(headers -> headers.setBasicAuth(user, pass))
                .retrieve()
                .body(QueueInfo.class);

        assert info != null;

        double queueLength = info.messages();
        double publishRate = info.message_stats().publish_details().rate();
        double ackRate = info.message_stats().ack_details().rate();

        double arrivalInterval = publishRate == 0 ? Double.MAX_VALUE : 1.0 / publishRate;
        double utilisation = ackRate == 0 ? 1.0 : publishRate / ackRate;
        double backlogGrowth = publishRate - ackRate;
        double tailLatency = ackRate == 0 ? Double.MAX_VALUE : queueLength / ackRate;

        return new QueueFeatures(
                queueLength,
                ackRate,
                arrivalInterval,
                utilisation,
                backlogGrowth,
                tailLatency
        );
    }

    public RoutingFeatures collectAll() {
        return new RoutingFeatures(
                Map.of(
                        "high", collect("priority-high"),
                        "medium", collect("priority-medium"),
                        "low", collect("priority-low")
                )
        );
    }
}