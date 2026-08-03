package ict.um.orders.core_api.queries;

public class GetOrderRoutingStatusesQuery {
    private final String orderId;

    public GetOrderRoutingStatusesQuery(String orderId) {
        this.orderId = orderId;
    }

    // Getter
    public String getOrderId() { return orderId; }
}

