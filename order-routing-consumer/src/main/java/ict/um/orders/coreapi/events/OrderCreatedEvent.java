package ict.um.orders.coreapi.events;

public class OrderCreatedEvent {

    private final String orderId;
    private final String customerId;
    private final String category;
    private final double orderValue;
    private final int itemCount;
    private final long timestamp;
    private final int priority;
    private final int sequenceNumber;
    private final String dataHash;

    public OrderCreatedEvent(String orderId,
                             String customerId,
                             String category,
                             double orderValue,
                             int itemCount,
                             long timestamp,
                             int priority,
                             int sequenceNumber,
                             String dataHash) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.category = category;
        this.orderValue = orderValue;
        this.itemCount = itemCount;
        this.timestamp = timestamp;
        this.priority = priority;
        this.sequenceNumber = sequenceNumber;
        this.dataHash = dataHash;
    }

    public String getOrderId() { return orderId; }
    public String getCustomerId() { return customerId; }
    public String getCategory() { return category; }
    public double getOrderValue() { return orderValue; }
    public int getItemCount() { return itemCount; }
    public long getTimestamp() { return timestamp; }
    public int getPriority() { return priority; }
    public int getSequenceNumber() { return sequenceNumber; }
    public String getDataHash() { return dataHash; }
}
