package ict.um.pharmaceutical.coreapi.events;

import org.axonframework.serialization.Revision;

@Revision("1.0")
public class ShipmentReportedEvent {
    private final String batchID;
    private final String toEntity;
    private final int quantitySold;

    public ShipmentReportedEvent(String batchID, String toEntity, int quantitySold) {
        this.batchID = batchID;
        this.toEntity = toEntity;
        this.quantitySold = quantitySold;
    }

    // Getters
    public String getBatchID() {
        return batchID;
    }
    public String getToEntity() {
        return toEntity;
    }
    public int getQuantitySold() {
        return quantitySold;
    }
}
