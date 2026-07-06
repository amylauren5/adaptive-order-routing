package ict.um.orders.coreapi.queries;

public class VerifyDataHashQuery {
    private final String orderId;

    public VerifyDataHashQuery(String orderId) {
        this.orderId = orderId;
    }

    // Getter
    public String getOrderId() { return orderId; }
}
