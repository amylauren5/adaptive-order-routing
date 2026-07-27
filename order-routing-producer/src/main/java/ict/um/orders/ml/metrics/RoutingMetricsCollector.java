package ict.um.orders.ml.metrics;

import ict.um.orders.core_api.config.QueueNames;
import ict.um.orders.ml.features.QueueFeatures;
import ict.um.orders.ml.features.RoutingFeatures;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class RoutingMetricsCollector {

    private static final double MAX_INTERVAL_SECONDS = 60.0;
    private static final double MAX_TAIL_LATENCY_SECONDS = 60.0;
    private static final double MAX_UTILISATION = 10.0;

    private final RestClient client;
    private final String user;
    private final String pass;

    public RoutingMetricsCollector(
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
                .uri("/queues/%2F/{queue}", queueName)
                .headers(headers -> headers.setBasicAuth(user, pass))
                .retrieve()
                .body(QueueInfo.class);

        if (info == null) {
            throw new IllegalStateException(
                    "RabbitMQ returned no metrics for queue: " + queueName
            );
        }

        double queueLength = info.messages();
        double publishRate = readPublishRate(info);
        double ackRate = readAckRate(info);

        double arrivalInterval = publishRate <= 0.0
                ? MAX_INTERVAL_SECONDS
                : Math.min(
                1.0 / publishRate,
                MAX_INTERVAL_SECONDS
        );

        double utilisation = ackRate <= 0.0
                ? (publishRate > 0.0 ? MAX_UTILISATION : 0.0)
                : Math.min(
                publishRate / ackRate,
                MAX_UTILISATION
        );

        double backlogGrowth = publishRate - ackRate;

        double tailLatency = ackRate <= 0.0
                ? (queueLength > 0.0
                ? MAX_TAIL_LATENCY_SECONDS
                : 0.0)
                : Math.min(
                queueLength / ackRate,
                MAX_TAIL_LATENCY_SECONDS
        );

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
                        "high", collect(QueueNames.HIGH),
                        "medium", collect(QueueNames.MEDIUM),
                        "low", collect(QueueNames.LOW)
                )
        );
    }

    private double readPublishRate(QueueInfo info) {
        if (info.message_stats() == null
                || info.message_stats().publish_details() == null) {
            return 0.0;
        }

        return Math.max(
                info.message_stats().publish_details().rate(),
                0.0
        );
    }

    private double readAckRate(QueueInfo info) {
        if (info.message_stats() == null
                || info.message_stats().ack_details() == null) {
            return 0.0;
        }

        return Math.max(
                info.message_stats().ack_details().rate(),
                0.0
        );
    }
}