package ict.um.orders.ml.features;

public record QueueFeatures(
        double queueLength,
        double consumerThroughput,
        double arrivalInterval,
        double utilisation,
        double backlogGrowth,
        double estimatedDelay
) {}