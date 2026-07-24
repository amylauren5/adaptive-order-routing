package ict.um.orders.core_api.queries;

public class GetCachedOrderStatusesQuery {
    private final String orderId;

    public GetCachedOrderStatusesQuery(String orderId) {
        this.orderId = orderId;
    }

    // Getter
    public String getOrderId() { return orderId; }
}

