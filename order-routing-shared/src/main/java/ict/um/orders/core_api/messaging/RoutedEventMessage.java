package ict.um.orders.core_api.messaging;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class RoutedEventMessage {

    private final String routingDecisionId;
    private final String selectedQueue;
    private final long publishedAt;

    private final RoutedEventType eventType;
    private final String payload;

    @JsonCreator
    public RoutedEventMessage(
            @JsonProperty("routingDecisionId") String routingDecisionId,
            @JsonProperty("selectedQueue") String selectedQueue,
            @JsonProperty("publishedAt") long publishedAt,
            @JsonProperty("eventType") RoutedEventType eventType,
            @JsonProperty("payload") String payload
    ) {
        this.routingDecisionId = routingDecisionId;
        this.selectedQueue = selectedQueue;
        this.publishedAt = publishedAt;
        this.eventType = eventType;
        this.payload = payload;
    }

    public String getRoutingDecisionId() {
        return routingDecisionId;
    }

    public String getSelectedQueue() {
        return selectedQueue;
    }

    public long getPublishedAt() {
        return publishedAt;
    }

    public RoutedEventType getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }
}