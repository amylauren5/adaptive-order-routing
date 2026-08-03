package ict.um.orders.core_api.commands;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

public class DispatchOrderCommand {
    @TargetAggregateIdentifier
    private final String orderId;
    private final long timestamp;

    public DispatchOrderCommand(String orderId, long timestamp) {
        this.orderId = orderId;
        this.timestamp = timestamp;
    }

    public String getOrderId() { return orderId; }
    public long getTimestamp() { return timestamp; }
}

