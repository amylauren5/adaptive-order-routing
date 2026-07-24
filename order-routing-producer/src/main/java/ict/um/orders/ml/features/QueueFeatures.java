package ict.um.orders.ml.features;

public record QueueFeatures(
        double queueLength,
        double consumerThroughput,
        double arrivalInterval,
        double utilisation,
        double backlogGrowth,
        double tailLatency
) {
    public double[] toVector() {
        return new double[]{
                queueLength,
                consumerThroughput,
                arrivalInterval,
                utilisation,
                backlogGrowth,
                tailLatency
        };
    }
}