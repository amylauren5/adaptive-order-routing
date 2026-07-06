package ict.um.pharmaceutical.coreapi.events;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

public class DistributorPriceRevisedEvent {
    @TargetAggregateIdentifier
    private final String batchID;
    private final double newPrice;

    public DistributorPriceRevisedEvent(String batchID, double newPrice) {
        this.batchID = batchID;
        this.newPrice = newPrice;
    }

    // Getters
    public String getBatchID() { return batchID; }
    public double getNewPrice() { return newPrice; }
}
