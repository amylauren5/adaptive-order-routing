package ict.um.orders.query_model.order_submitted;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_submitted_view")
public class OrderSubmittedView {

    @Id
    private String orderId;

    private String customerId;
    private String category;
    private double orderValue;
    private int itemCount;

    // Required for hash reconstruction
    private long timestamp;
    private int priority;
    private int sequenceNumber;

    private String status;
    private long lastEventTimestamp;
    private int lastSequenceNumber;

    public OrderSubmittedView() {}

    public OrderSubmittedView(String orderId,
                              String customerId,
                              String category,
                              double orderValue,
                              int itemCount,
                              long timestamp,
                              int priority,
                              int sequenceNumber,
                              String status,
                              long lastEventTimestamp,
                              int lastSequenceNumber) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.category = category;
        this.orderValue = orderValue;
        this.itemCount = itemCount;
        this.timestamp = timestamp;
        this.priority = priority;
        this.sequenceNumber = sequenceNumber;
        this.status = status;
        this.lastEventTimestamp = lastEventTimestamp;
        this.lastSequenceNumber = lastSequenceNumber;
    }

    public String getOrderId() { return orderId; }
    public String getCustomerId() { return customerId; }
    public String getCategory() { return category; }
    public double getOrderValue() { return orderValue; }
    public int getItemCount() { return itemCount; }

    public long getTimestamp() { return timestamp; }
    public int getPriority() { return priority; }
    public int getSequenceNumber() { return sequenceNumber; }

    public String getStatus() { return status; }
    public long getLastEventTimestamp() { return lastEventTimestamp; }
    public int getLastSequenceNumber() { return lastSequenceNumber; }

    public void setStatus(String status) { this.status = status; }
    public void setLastEventTimestamp(long ts) { this.lastEventTimestamp = ts; }
    public void setLastSequenceNumber(int seq) { this.lastSequenceNumber = seq; }
}
