package ict.um.orders.coreapi.commands;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

public class DispatchOrderCommand {
    @TargetAggregateIdentifier
    private final String orderId;
    private final long timestamp;
    private final int sequenceNumber;

    public DispatchOrderCommand(String orderId, long timestamp, int sequenceNumber) {
        this.orderId = orderId;
        this.timestamp = timestamp;
        this.sequenceNumber = sequenceNumber;
    }

    public String getOrderId() { return orderId; }
    public long getTimestamp() { return timestamp; }
    public int getSequenceNumber() { return sequenceNumber; }
}

