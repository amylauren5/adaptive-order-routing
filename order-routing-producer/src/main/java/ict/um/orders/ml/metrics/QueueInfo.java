package ict.um.orders.ml.metrics;

public record QueueInfo(
        int messages,
        MessageStats message_stats
) {}
