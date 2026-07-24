package ict.um.orders.ml.metrics;

public record MessageStats(
        RateDetails publish_details,
        RateDetails ack_details
) {}
