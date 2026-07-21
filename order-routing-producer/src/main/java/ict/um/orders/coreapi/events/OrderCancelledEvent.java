package ict.um.orders.coreapi.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class OrderCancelledEvent {

    private final String orderId;
    private final long timestamp;
    private final int sequenceNumber;
    private final String reason;

    @JsonCreator
    public OrderCancelledEvent(
            @JsonProperty("orderId") String orderId,
            @JsonProperty("timestamp") long timestamp,
            @JsonProperty("sequenceNumber") int sequenceNumber,
            @JsonProperty("reason") String reason) {
        this.orderId = orderId;
        this.timestamp = timestamp;
        this.sequenceNumber = sequenceNumber;
        this.reason = reason;
    }

    public String getOrderId() { return orderId; }
    public long getTimestamp() { return timestamp; }
    public int getSequenceNumber() { return sequenceNumber; }
    public String getReason() { return reason; }
}
