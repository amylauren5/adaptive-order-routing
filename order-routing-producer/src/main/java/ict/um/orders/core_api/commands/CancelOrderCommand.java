package ict.um.orders.core_api.commands;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

public class CancelOrderCommand {

    @TargetAggregateIdentifier
    private final String orderId;
    private final long timestamp;
    private final String reason;   // optional but recommended

    public CancelOrderCommand(String orderId,
                              long timestamp,
                              String reason) {
        this.orderId = orderId;
        this.timestamp = timestamp;
        this.reason = reason;
    }

    public String getOrderId() {
        return orderId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getReason() {
        return reason;
    }
}


