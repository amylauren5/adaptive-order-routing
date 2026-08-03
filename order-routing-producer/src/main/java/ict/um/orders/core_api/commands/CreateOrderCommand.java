package ict.um.orders.core_api.commands;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

public class CreateOrderCommand {

    @TargetAggregateIdentifier
    private final String orderId;

    private final String customerId;
    private final String category;
    private final double orderValue;
    private final int itemCount;
    private final long timestamp;
    private final String dataHash;

    public CreateOrderCommand(String orderId,
                              String customerId,
                              String category,
                              double orderValue,
                              int itemCount,
                              long timestamp,
                              String dataHash) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.category = category;
        this.orderValue = orderValue;
        this.itemCount = itemCount;
        this.timestamp = timestamp;
        this.dataHash = dataHash;
    }

    public String getOrderId() { return orderId; }
    public String getCustomerId() { return customerId; }
    public String getCategory() { return category; }
    public double getOrderValue() { return orderValue; }
    public int getItemCount() { return itemCount; }
    public long getTimestamp() { return timestamp; }
    public String getDataHash() { return dataHash; }
}
