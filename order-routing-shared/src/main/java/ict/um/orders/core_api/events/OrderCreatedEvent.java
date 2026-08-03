package ict.um.orders.core_api.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class OrderCreatedEvent {

    private final String orderId;
    private final String customerId;
    private final String category;
    private final double orderValue;
    private final int itemCount;
    private final long timestamp;
    private final int sequenceNumber;
    private final String dataHash;

    @JsonCreator
    public OrderCreatedEvent(
            @JsonProperty("orderId") String orderId,
            @JsonProperty("customerId") String customerId,
            @JsonProperty("category") String category,
            @JsonProperty("orderValue") double orderValue,
            @JsonProperty("itemCount") int itemCount,
            @JsonProperty("timestamp") long timestamp,
            @JsonProperty("sequenceNumber") int sequenceNumber,
            @JsonProperty("dataHash") String dataHash) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.category = category;
        this.orderValue = orderValue;
        this.itemCount = itemCount;
        this.timestamp = timestamp;
        this.sequenceNumber = sequenceNumber;
        this.dataHash = dataHash;
    }

    public String getOrderId() { return orderId; }
    public String getCustomerId() { return customerId; }
    public String getCategory() { return category; }
    public double getOrderValue() { return orderValue; }
    public int getItemCount() { return itemCount; }
    public long getTimestamp() { return timestamp; }
    public int getSequenceNumber() { return sequenceNumber; }
    public String getDataHash() { return dataHash; }
}
