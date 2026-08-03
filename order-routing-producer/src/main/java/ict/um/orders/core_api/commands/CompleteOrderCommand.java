package ict.um.orders.core_api.commands;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

public class CompleteOrderCommand {
    @TargetAggregateIdentifier
    private final String orderId;
    private final long timestamp;

    public CompleteOrderCommand(String orderId, long timestamp) {
        this.orderId = orderId;
        this.timestamp = timestamp;
    }

    public String getOrderId() { return orderId; }
    public long getTimestamp() { return timestamp; }
}


