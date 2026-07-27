package ict.um.orders.core_api.messaging;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class RoutedEventMessage {

    private final RoutedEventType eventType;
    private final String payload;

    @JsonCreator
    public RoutedEventMessage(
            @JsonProperty("eventType") RoutedEventType eventType,
            @JsonProperty("payload") String payload
    ) {
        this.eventType = eventType;
        this.payload = payload;
    }

    public RoutedEventType getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }
}