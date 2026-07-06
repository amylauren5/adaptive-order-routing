package ict.um.orders.coreapi.commands;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

public class CreateOrderCommand {

    @TargetAggregateIdentifier
    private final String orderId;
    private final String customerId;

    private final String category;
    private final double orderValue;
    private final int itemCount;

    private final long timestamp;
    private final int priority;
    private final int sequenceNumber;

    public CreateOrderCommand(String orderId,
                              String customerId,
                              String category,
                              double orderValue,
                              int itemCount,
                              long timestamp,
                              int priority,
                              int sequenceNumber) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.category = category;
        this.orderValue = orderValue;
        this.itemCount = itemCount;
        this.timestamp = timestamp;
        this.priority = priority;
        this.sequenceNumber = sequenceNumber;
    }

    public String getOrderId() { return orderId; }
    public String getCustomerId() { return customerId; }
    public String getCategory() { return category; }
    public double getOrderValue() { return orderValue; }
    public int getItemCount() { return itemCount; }
    public long getTimestamp() { return timestamp; }
    public int getPriority() { return priority; }
    public int getSequenceNumber() { return sequenceNumber; }
}