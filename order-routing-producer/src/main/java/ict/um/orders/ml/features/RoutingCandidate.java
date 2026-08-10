package ict.um.orders.ml.features;

import ict.um.orders.routing.OrderRoutingContext;

public record RoutingCandidate(
        OrderRoutingContext context,
        QueueFeatures queueFeatures
) {
}