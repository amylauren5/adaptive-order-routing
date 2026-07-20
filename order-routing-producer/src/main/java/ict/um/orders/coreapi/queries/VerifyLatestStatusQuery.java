package ict.um.orders.coreapi.queries;

public class VerifyLatestStatusQuery {
    private final String orderId;

    public VerifyLatestStatusQuery(String orderId) {
        this.orderId = orderId;
    }

    // Getter
    public String getOrderId() { return orderId; }
}
