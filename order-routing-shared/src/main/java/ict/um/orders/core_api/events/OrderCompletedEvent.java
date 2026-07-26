package ict.um.orders.core_api.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class OrderCompletedEvent {

    private final String orderId;
    private final long timestamp;
    private final int sequenceNumber;

    @JsonCreator
    public OrderCompletedEvent(
            @JsonProperty("orderId") String orderId,
            @JsonProperty("timestamp") long timestamp,
            @JsonProperty("sequenceNumber") int sequenceNumber) {
        this.orderId = orderId;
        this.timestamp = timestamp;
        this.sequenceNumber = sequenceNumber;
    }

    public String getOrderId() { return orderId; }
    public long getTimestamp() { return timestamp; }
    public int getSequenceNumber() { return sequenceNumber; }
}
