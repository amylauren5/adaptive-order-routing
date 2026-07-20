package ict.um.orders.query_model.order_cached;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_cached_view")
public class OrderCachedView {

    @Id
    private String orderId;

    private String status;
    private String category;
    private double orderValue;
    private int itemCount;
    private long lastEventTimestamp;

    public OrderCachedView() {}

    public OrderCachedView(String orderId,
                           String status,
                           String category,
                           double orderValue,
                           int itemCount,
                           long lastEventTimestamp) {
        this.orderId = orderId;
        this.status = status;
        this.category = category;
        this.orderValue = orderValue;
        this.itemCount = itemCount;
        this.lastEventTimestamp = lastEventTimestamp;
    }

    public String getOrderId() { return orderId; }
    public String getStatus() { return status; }
    public String getCategory() { return category; }
    public double getOrderValue() { return orderValue; }
    public int getItemCount() { return itemCount; }
    public long getLastEventTimestamp() { return lastEventTimestamp; }

    public void setStatus(String status) { this.status = status; }
    public void setLastEventTimestamp(long ts) { this.lastEventTimestamp = ts; }
}
