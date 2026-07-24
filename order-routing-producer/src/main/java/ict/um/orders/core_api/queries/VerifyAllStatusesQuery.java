package ict.um.orders.core_api.queries;

public class VerifyAllStatusesQuery {
    private final String orderId;

    public VerifyAllStatusesQuery(String orderId) {
        this.orderId = orderId;
    }

    // Getter
    public String getOrderId() { return orderId; }
}
