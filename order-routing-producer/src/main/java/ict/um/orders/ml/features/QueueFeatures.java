package ict.um.orders.ml.features;

public record QueueFeatures(
        double queueLength,
        double arrivalRate,
        double consumerThroughput,
        double utilisation,
        double backlogGrowth
) {
}