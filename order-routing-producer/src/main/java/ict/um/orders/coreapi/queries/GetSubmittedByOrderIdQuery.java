package ict.um.orders.coreapi.queries;

public class GetSubmittedByOrderIdQuery {
    private final String orderId;

    public GetSubmittedByOrderIdQuery(String orderId) {
        this.orderId = orderId;
    }

    // Getter
    public String getOrderId() { return orderId; }
}
