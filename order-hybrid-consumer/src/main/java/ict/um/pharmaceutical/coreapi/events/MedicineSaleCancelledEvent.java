package ict.um.pharmaceutical.coreapi.events;

import org.axonframework.serialization.Revision;

@Revision("1.0")
public class MedicineSaleCancelledEvent {
    private final String batchID;

    public MedicineSaleCancelledEvent(String batchID) {
        this.batchID = batchID;
    }

    // Getter
    public String getBatchID() { return batchID; }
}

