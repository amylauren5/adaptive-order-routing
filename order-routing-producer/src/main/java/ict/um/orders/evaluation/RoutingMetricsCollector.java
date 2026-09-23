package ict.um.orders.evaluation;

import ict.um.orders.core_api.config.QueueNames;
import ict.um.orders.ml.features.QueueFeatures;
import ict.um.orders.ml.features.RoutingFeatures;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class RoutingMetricsCollector {

    private static final Logger logger =
            LoggerFactory.getLogger(RoutingMetricsCollector.class);

    private static final double MAX_UTILISATION = 10.0;

    private final RestClient client;
    private final String virtualHost;

    private final AtomicReference<RoutingFeatures> latestSnapshot =
            new AtomicReference<>();

    private final Map<String, BacklogObservation> backlogObservations =
            new ConcurrentHashMap<>();

    private final AtomicLong lastRefreshDurationNs =
            new AtomicLong();

    private final AtomicLong lastRefreshTimestampMs =
            new AtomicLong();

    public RoutingMetricsCollector(
            @Value("${rabbit.mgmt.host}") String host,
            @Value("${rabbit.mgmt.user}") String user,
            @Value("${rabbit.mgmt.pass}") String pass,
            @Value("${rabbit.mgmt.vhost:/}") String virtualHost
    ) {
        this.virtualHost = virtualHost;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);

        requestFactory.setReadTimeout(
                Duration.ofSeconds(3)
        );

        this.client = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(host + "/api")
                .defaultHeaders(headers ->
                        headers.setBasicAuth(user, pass))
                .build();
    }

    /**
     * Returns the most recently completed queue-state snapshot.
     *
     * Routing decisions therefore do not normally make synchronous
     * RabbitMQ Management API calls.
     */
    public RoutingFeatures collectAll() {
        RoutingFeatures snapshot =
                latestSnapshot.get();

        if (snapshot != null) {
            return snapshot;
        }

        /*
         * Startup fallback only. This may occur before the first
         * scheduled refresh has completed.
         */
        return refreshNow();
    }

    @Scheduled(
            fixedDelayString =
                    "${routing.queue-state-refresh-ms:1000}"
    )
    public void refreshSnapshot() {
        try {
            refreshNow();
        } catch (RuntimeException exception) {
            logger.warn(
                    "Failed to refresh RabbitMQ queue-state snapshot",
                    exception
            );
        }
    }

    public long getLastRefreshDurationNs() {
        return lastRefreshDurationNs.get();
    }

    public long getLastRefreshTimestampMs() {
        return lastRefreshTimestampMs.get();
    }

    private synchronized RoutingFeatures refreshNow() {
        long startedAtNs =
                System.nanoTime();

        long observationTimestampNs =
                System.nanoTime();

        QueueFeatures queue1 =
                fetchFeatures(
                        QueueNames.QUEUE_1,
                        observationTimestampNs
                );

        QueueFeatures queue2 =
                fetchFeatures(
                        QueueNames.QUEUE_2,
                        observationTimestampNs
                );

        QueueFeatures queue3 =
                fetchFeatures(
                        QueueNames.QUEUE_3,
                        observationTimestampNs
                );

        RoutingFeatures snapshot =
                new RoutingFeatures(
                        Map.of(
                                QueueNames.QUEUE_1, queue1,
                                QueueNames.QUEUE_2, queue2,
                                QueueNames.QUEUE_3, queue3
                        )
                );

        latestSnapshot.set(snapshot);

        lastRefreshTimestampMs.set(
                System.currentTimeMillis()
        );

        lastRefreshDurationNs.set(
                System.nanoTime() - startedAtNs
        );

        return snapshot;
    }

    private QueueFeatures fetchFeatures(
            String queueName,
            long observationTimestampNs
    ) {
        QueueInfo info =
                fetchQueueInfo(queueName);

        double queueLength =
                Math.max(
                        info.messages_ready(),
                        0.0
                );

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
                calculateBacklogGrowth(
                        queueName,
                        queueLength,
                        observationTimestampNs
                );

        return new QueueFeatures(
                queueLength,
                arrivalRate,
                consumerThroughput,
                utilisation,
                backlogGrowth
        );
    }

    private QueueInfo fetchQueueInfo(
            String queueName
    ) {
        try {
            QueueInfo info = client.get()
                    .uri(uriBuilder ->
                            uriBuilder
                                    .pathSegment(
                                            "queues",
                                            virtualHost,
                                            queueName
                                    )
                                    .build()
                    )
                    .retrieve()
                    .body(QueueInfo.class);

            if (info == null) {
                throw new IllegalStateException(
                        "RabbitMQ returned an empty response for queue: "
                                + queueName
                );
            }

            return info;

        } catch (
                HttpClientErrorException.NotFound exception
        ) {
            throw new IllegalStateException(
                    "RabbitMQ queue '%s' was not found "
                            + "in virtual host '%s'"
                            .formatted(
                                    queueName,
                                    virtualHost
                            ),
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

    private double calculateBacklogGrowth(
            String queueName,
            double currentQueueLength,
            long nowNs
    ) {
        final double[] growthHolder = {0.0};

        backlogObservations.compute(
                queueName,
                (key, previous) -> {

                    double growth = 0.0;

                    if (previous != null) {
                        double elapsedSeconds =
                                (
                                        nowNs
                                                - previous.timestampNanos()
                                )
                                        / 1_000_000_000.0;

                        if (elapsedSeconds > 0.0) {
                            growth =
                                    (
                                            currentQueueLength
                                                    - previous.queueLength()
                                    )
                                            / elapsedSeconds;
                        }

                        if (!Double.isFinite(growth)) {
                            growth = 0.0;
                        }
                    }

                    growthHolder[0] = growth;

                    return new BacklogObservation(
                            currentQueueLength,
                            nowNs
                    );
                }
        );

        return growthHolder[0];
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
                arrivalRate
                        / consumerThroughput;

        if (!Double.isFinite(utilisation)) {
            return MAX_UTILISATION;
        }

        return Math.min(
                Math.max(
                        utilisation,
                        0.0
                ),
                MAX_UTILISATION
        );
    }

    private double readPublishRate(
            QueueInfo info
    ) {
        if (
                info.message_stats() == null
                        || info.message_stats()
                        .publish_details() == null
        ) {
            return 0.0;
        }

        return Math.max(
                info.message_stats()
                        .publish_details()
                        .rate(),
                0.0
        );
    }

    private double readAckRate(
            QueueInfo info
    ) {
        if (
                info.message_stats() == null
                        || info.message_stats()
                        .ack_details() == null
        ) {
            return 0.0;
        }

        return Math.max(
                info.message_stats()
                        .ack_details()
                        .rate(),
                0.0
        );
    }

    private record BacklogObservation(
            double queueLength,
            long timestampNanos
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