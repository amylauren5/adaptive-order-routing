package ict.um.orders.query_model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_view")
public class OrderView {

    @Id
    private String orderId;

    private String status;
    private String category;
    private double orderValue;
    private int itemCount;
    private long lastEventTimestamp;
    private int lastProcessedSequenceNumber;

    public OrderView() {}

    public OrderView(String orderId,
                     String status,
                     String category,
                     double orderValue,
                     int itemCount,
                     long lastEventTimestamp,
                     int lastProcessedSequenceNumber) {
        this.orderId = orderId;
        this.status = status;
        this.category = category;
        this.orderValue = orderValue;
        this.itemCount = itemCount;
        this.lastEventTimestamp = lastEventTimestamp;
        this.lastProcessedSequenceNumber = lastProcessedSequenceNumber;
    }

    public String getOrderId() { return orderId; }
    public String getStatus() { return status; }
    public String getCategory() { return category; }
    public double getOrderValue() { return orderValue; }
    public int getItemCount() { return itemCount; }
    public long getLastEventTimestamp() { return lastEventTimestamp; }
    public int getLastProcessedSequenceNumber() { return lastProcessedSequenceNumber; }

    public void setStatus(String status) { this.status = status; }
    public void setLastEventTimestamp(long ts) { this.lastEventTimestamp = ts; }
    public void setLastProcessedSequenceNumber(int l) { this.lastProcessedSequenceNumber = l; }
}
