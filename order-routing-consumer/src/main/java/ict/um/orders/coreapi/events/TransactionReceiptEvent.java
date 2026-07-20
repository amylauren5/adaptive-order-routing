package ict.um.orders.coreapi.events;

public class TransactionReceiptEvent {
    private final String transactionHash;
    private final String orderId;

    public TransactionReceiptEvent(String transactionHash, String orderId) {
        this.transactionHash = transactionHash;
        this.orderId = orderId;
    }

    public String getTransactionHash() {
        return transactionHash;
    }

    public String getOrderId() {
        return orderId;
    }
}
