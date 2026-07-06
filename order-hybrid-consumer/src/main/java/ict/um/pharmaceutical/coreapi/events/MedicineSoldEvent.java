package ict.um.pharmaceutical.coreapi.events;

import org.axonframework.serialization.Revision;

@Revision("1.0")
public class MedicineSoldEvent {
    private final String batchID;
    private final String toEntity;
    private final String toAddress;
    private final int quantity;

    public MedicineSoldEvent(String batchID, String toAddress, String toEntity, int quantity) {
        this.batchID = batchID;
        this.toEntity = toEntity;
        this.toAddress = toAddress;
        this.quantity = quantity;
    }

    public String getBatchID() {
        return batchID;
    }

    public String getToAddress() {
        return toAddress;
    }

    public String getToEntity() { return toEntity; }

    public int getQuantity() {
        return quantity;
    }
}

