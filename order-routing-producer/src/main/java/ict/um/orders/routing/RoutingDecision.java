package ict.um.orders.routing;

public record RoutingDecision(
        String routingDecisionId,
        String selectedQueue,
        long modelInferenceNs
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

        if (modelInferenceNs < 0L) {
            throw new IllegalArgumentException(
                    "Model inference time must not be negative"
            );
        }
    }
}