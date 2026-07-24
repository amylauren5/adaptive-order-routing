package ict.um.orders.core_api.queries;

public class GetCacheByOrderIdQuery {
    private final String orderId;

    public GetCacheByOrderIdQuery(String orderId) {
        this.orderId = orderId;
    }

    // Getter
    public String getOrderId() { return orderId; }
}
