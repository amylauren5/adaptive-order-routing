package ict.um.orders.evaluation;

import ict.um.orders.core_api.config.QueueNames;
import ict.um.orders.ml.features.QueueFeatures;
import ict.um.orders.ml.features.RoutingFeatures;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.scheduling.annotation.Scheduled;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RoutingMetricsCollector {

    private static final Logger logger =
            LoggerFactory.getLogger(RoutingMetricsCollector.class);

    private static final double MAX_UTILISATION = 10.0;

    private final RestClient client;
    private final String virtualHost;
    private final Map<String, BacklogObservation> backlogObservations =
            new ConcurrentHashMap<>();

    public RoutingMetricsCollector(
            @Value("${rabbit.mgmt.host}") String host,
            @Value("${rabbit.mgmt.user}") String user,
            @Value("${rabbit.mgmt.pass}") String pass,
            @Value("${rabbit.mgmt.vhost:/}") String virtualHost
    ) {

        this.virtualHost = virtualHost;

        this.client = RestClient.builder()
                .baseUrl(host + "/api")
                .defaultHeaders(headers ->
                        headers.setBasicAuth(user, pass))
                .build();
    }

    public QueueFeatures collect(String queueName) {
        QueueInfo info = fetchQueueInfo(queueName);

        double queueLength =
                Math.max(info.messages_ready(), 0.0);

        double arrivalRate =
                readPublishRate(info);

        double consumerThroughput =
                readAckRate(info);

        double utilisation =
                calculateUtilisation(
                        arrivalRate,
                        consumerThroughput
                );

        double backlogGrowth =
                calculateBacklogGrowth(queueName);

        return new QueueFeatures(
                queueLength,
                arrivalRate,
                consumerThroughput,
                utilisation,
                backlogGrowth
        );
    }

    public RoutingFeatures collectAll() {
        return new RoutingFeatures(
                Map.of(
                        QueueNames.QUEUE_1,
                        collect(QueueNames.QUEUE_1),

                        QueueNames.QUEUE_2,
                        collect(QueueNames.QUEUE_2),

                        QueueNames.QUEUE_3,
                        collect(QueueNames.QUEUE_3)
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

    private double calculateBacklogGrowth(String queueName) {
        BacklogObservation observation =
                backlogObservations.get(queueName);

        if (observation == null) {
            return 0.0;
        }

        return observation.growth();
    }

    private double calculateUtilisation(
            double arrivalRate,
            double consumerThroughput
    ) {
        if (consumerThroughput <= 0.0) {
            return arrivalRate > 0.0
                    ? MAX_UTILISATION
                    : 0.0;
        }

        double utilisation =
                arrivalRate / consumerThroughput;

        if (!Double.isFinite(utilisation)) {
            return MAX_UTILISATION;
        }

        return Math.min(
                Math.max(utilisation, 0.0),
                MAX_UTILISATION
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

    @Scheduled(
            fixedRateString = "${routing.backlog-window-ms:1000}"
    )
    public void sampleBacklogGrowth() {
        sampleBacklogGrowthSafely(QueueNames.QUEUE_1);
        sampleBacklogGrowthSafely(QueueNames.QUEUE_2);
        sampleBacklogGrowthSafely(QueueNames.QUEUE_3);
    }

    private void sampleBacklogGrowthSafely(String queueName) {
        try {
            sampleBacklogGrowth(queueName);
        } catch (RuntimeException exception) {
            logger.warn(
                    "Failed to sample backlog growth for queue {}",
                    queueName,
                    exception
            );
        }
    }

    private void sampleBacklogGrowth(String queueName) {
        QueueInfo info = fetchQueueInfo(queueName);

        double currentQueueLength =
                Math.max(info.messages_ready(), 0.0);

        long now = System.nanoTime();

        backlogObservations.compute(
                queueName,
                (key, previous) -> {

                    if (previous == null) {
                        return new BacklogObservation(
                                currentQueueLength,
                                now,
                                0.0
                        );
                    }

                    double elapsedSeconds =
                            (now - previous.timestampNanos())
                                    / 1_000_000_000.0;

                    double growth = 0.0;

                    if (elapsedSeconds > 0.0) {
                        growth =
                                (currentQueueLength
                                        - previous.queueLength())
                                        / elapsedSeconds;
                    }

                    if (!Double.isFinite(growth)) {
                        growth = 0.0;
                    }

                    return new BacklogObservation(
                            currentQueueLength,
                            now,
                            growth
                    );
                }
        );
    }

    private record BacklogObservation(
            double queueLength,
            long timestampNanos,
            double growth
    ) {
    }

    private record QueueInfo(
            int messages,
            int messages_ready,
            int messages_unacknowledged,
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