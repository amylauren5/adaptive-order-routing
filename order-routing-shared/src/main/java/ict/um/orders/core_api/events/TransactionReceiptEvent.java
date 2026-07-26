package ict.um.orders.core_api.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class TransactionReceiptEvent {
    private final String transactionHash;
    private final String orderId;

    @JsonCreator
    public TransactionReceiptEvent(
            @JsonProperty("transactionHash") String transactionHash,
            @JsonProperty("orderId") String orderId) {
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
