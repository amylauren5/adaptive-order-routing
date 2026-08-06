package ict.um.orders.ml.metrics;

import ict.um.orders.core_api.config.QueueNames;
import ict.um.orders.ml.features.QueueFeatures;
import ict.um.orders.ml.features.RoutingFeatures;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Component
public class RoutingMetricsCollector {

    private static final double MAX_INTERVAL_SECONDS = 60.0;
    private static final double MAX_TAIL_LATENCY_SECONDS = 60.0;
    private static final double MAX_UTILISATION = 10.0;

    private final RestClient client;
    private final String virtualHost;

    public RoutingMetricsCollector(
            @Value("${rabbit.mgmt.host}") String host,
            @Value("${rabbit.mgmt.user}") String user,
            @Value("${rabbit.mgmt.pass}") String pass,
            @Value("${rabbit.mgmt.vhost:/}") String virtualHost
    ) {
        this.virtualHost = virtualHost;

        this.client = RestClient.builder()
                .baseUrl(host + "/api")
                .defaultHeaders(headers -> headers.setBasicAuth(user, pass))
                .build();
    }

    public QueueFeatures collect(String queueName) {
        QueueInfo info = fetchQueueInfo(queueName);

        double queueLength = Math.max(info.messages(), 0.0);
        double publishRate = readPublishRate(info);
        double ackRate = readAckRate(info);

        double arrivalInterval = calculateArrivalInterval(publishRate);
        double utilisation = calculateUtilisation(publishRate, ackRate);
        double backlogGrowth = publishRate - ackRate;
        double tailLatency = calculateTailLatency(queueLength, ackRate);

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
                        "queue1", collect(QueueNames.QUEUE_1),
                        "queue2", collect(QueueNames.QUEUE_2),
                        "queue3", collect(QueueNames.QUEUE_3)
                )
        );
    }

    private QueueInfo fetchQueueInfo(String queueName) {
        try {
            QueueInfo info = client.get()
                    .uri(uriBuilder -> uriBuilder
                            .pathSegment("queues", virtualHost, queueName)
                            .build())
                    .retrieve()
                    .body(QueueInfo.class);

            if (info == null) {
                throw new IllegalStateException(
                        "RabbitMQ returned an empty response for queue: "
                                + queueName
                );
            }

            return info;
        } catch (HttpClientErrorException.NotFound exception) {
            throw new IllegalStateException(
                    "RabbitMQ queue '%s' was not found in virtual host '%s'"
                            .formatted(queueName, virtualHost),
                    exception
            );
        } catch (RestClientException exception) {
            throw new IllegalStateException(
                    "Failed to retrieve RabbitMQ metrics for queue: "
                            + queueName,
                    exception
            );
        }
    }

    private double calculateArrivalInterval(double publishRate) {
        if (publishRate <= 0.0) {
            return MAX_INTERVAL_SECONDS;
        }

        return Math.min(
                1.0 / publishRate,
                MAX_INTERVAL_SECONDS
        );
    }

    private double calculateUtilisation(
            double publishRate,
            double ackRate
    ) {
        if (ackRate <= 0.0) {
            return publishRate > 0.0
                    ? MAX_UTILISATION
                    : 0.0;
        }

        return Math.min(
                publishRate / ackRate,
                MAX_UTILISATION
        );
    }

    private double calculateTailLatency(
            double queueLength,
            double ackRate
    ) {
        if (ackRate <= 0.0) {
            return queueLength > 0.0
                    ? MAX_TAIL_LATENCY_SECONDS
                    : 0.0;
        }

        return Math.min(
                queueLength / ackRate,
                MAX_TAIL_LATENCY_SECONDS
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

    private record QueueInfo(
            int messages,
            MessageStats message_stats
    ) {
    }

    private record MessageStats(
            RateDetails publish_details,
            RateDetails ack_details
    ) {
    }

    private record RateDetails(
            double rate
    ) {
    }
}