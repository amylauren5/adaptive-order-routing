package ict.um.pharmaceutical.coreapi.events;

import ict.um.pharmaceutical.coreapi.enums.State;
import org.axonframework.serialization.Revision;

@Revision("1.0")
public class BatchUploadedEvent {
    private String manufacturerAddress;
    private String batchID;
    private String productCode;
    private String productName;
    private String productDescription;
    private String expirationDate;
    private double price;
    private int quantity;
    private String dataHash;
    private State state;

    // Default constructor
   public BatchUploadedEvent(){

    }

    public BatchUploadedEvent(String address, String batchID, String productCode,
                              String productName, String productDescription,
                              String expirationDate, double price, int quantity,
                              String dataHash, State state) {
        this.manufacturerAddress = address;
        this.batchID = batchID;
        this.productCode = productCode;
        this.productName = productName;
        this.productDescription = productDescription;
        this.expirationDate = expirationDate;
        this.price = price;
        this.quantity = quantity;
        this.dataHash = dataHash;
        this.state = state;
    }

    // Getters

    public String getManufacturerAddress() {
        return manufacturerAddress;
    }

    public String getBatchID() {
        return batchID;
    }

    public String getProductCode() {
        return productCode;
    }

    public String getProductName() {
        return productName;
    }

    public String getProductDescription() {
        return productDescription;
    }

    public String getExpirationDate() {
        return expirationDate;
    }

    public double getPrice() {
        return price;
    }

    public String getDataHash() {
        return dataHash;
    }

    public State getState() {
        return state;
    }

    public int getQuantity() { return quantity; }
}

