package ict.um.orders.query_model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_routing_view")
public class OrderRoutingView {

    @Id
    private String orderId;
    private String customerId;

    private String status;
    private String category;
    private double orderValue;
    private int itemCount;
    private long createdAt;
    private long lastEventTimestamp;
    private int lastSequenceNumber;

    public OrderRoutingView() {}

    public OrderRoutingView(String orderId,
                            String customerId,
                            String status,
                            String category,
                            double orderValue,
                            int itemCount,
                            long createdAt,
                            long lastEventTimestamp,
                            int lastSequenceNumber) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.status = status;
        this.category = category;
        this.orderValue = orderValue;
        this.itemCount = itemCount;
        this.createdAt = createdAt;
        this.lastEventTimestamp = lastEventTimestamp;
        this.lastSequenceNumber = lastSequenceNumber;
    }

    public String getOrderId() { return orderId; }
    public String getCustomerId() { return customerId; }
    public String getStatus() { return status; }
    public String getCategory() { return category; }
    public double getOrderValue() { return orderValue; }
    public int getItemCount() { return itemCount; }
    public long getCreatedAt() { return createdAt; }
    public long getLastEventTimestamp() { return lastEventTimestamp; }
    public int getLastSequenceNumber() { return lastSequenceNumber; }

    public void setStatus(String status) { this.status = status; }
    public void setLastEventTimestamp(long ts) { this.lastEventTimestamp = ts; }
    public void setLastSequenceNumber(int sequenceNumber) { this.lastSequenceNumber = sequenceNumber; }
}
