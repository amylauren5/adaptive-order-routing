package ict.um.orders.core_api.queries;

public class GetOrderRoutingByOrderIdQuery {
    private final String orderId;

    public GetOrderRoutingByOrderIdQuery(String orderId) {
        this.orderId = orderId;
    }

    // Getter
    public String getOrderId() { return orderId; }
}
