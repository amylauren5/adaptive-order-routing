package ict.um.orders.routing;

public record RoutingDecision(
        String routingDecisionId,
        String selectedQueue
) {
    public RoutingDecision {
        if (routingDecisionId == null || routingDecisionId.isBlank()) {
            throw new IllegalArgumentException(
                    "Routing decision ID is required"
            );
        }

        if (selectedQueue == null || selectedQueue.isBlank()) {
            throw new IllegalArgumentException(
                    "Selected queue is required"
            );
        }
    }
}