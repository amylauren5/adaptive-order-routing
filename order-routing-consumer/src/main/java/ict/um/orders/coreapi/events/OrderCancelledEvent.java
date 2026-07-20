package ict.um.orders.coreapi.events;

public class OrderCancelledEvent {

    private final String orderId;
    private final long timestamp;
    private final int sequenceNumber;
    private final String reason;

    public OrderCancelledEvent(String orderId,
                               long timestamp,
                               int sequenceNumber,
                               String reason) {
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
