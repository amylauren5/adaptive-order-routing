package ict.um.pharmaceutical.coreapi.events;

import org.axonframework.serialization.Revision;

@Revision("1.0")
public class DeliveryReportedEvent {
    private final String batchID;
    private final String toEntity;

    public DeliveryReportedEvent(String batchID, String toEntity) {
        this.batchID = batchID;
        this.toEntity = toEntity;
    }

    // Getters
    public String getBatchID() { return batchID; }
    public String getToEntity() { return toEntity; }
}

