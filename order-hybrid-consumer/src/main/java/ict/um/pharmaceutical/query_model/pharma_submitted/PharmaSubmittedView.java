package ict.um.pharmaceutical.query_model.pharma_submitted;

import ict.um.pharmaceutical.coreapi.enums.State;
import jakarta.persistence.*;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "pharma_submitted_view")
public class PharmaSubmittedView {
    @Id
    private String batchID;

    // Transaction header
    @ElementCollection
    private List<String> transactionHashList; // Transaction hash of the block
    private long timestamp; // Unix time format
    private String fromEntity; // Stakeholder initiating tracking
    private String toEntity; // Stakeholder destination

    // Batch details
    private String productCode;
    private String productName;
    private String productDescription;
    private String expirationDate;
    private double manufacturerPrice;
    private double distributorPrice;
    private int quantity;

    // Identity details
    private String manufacturerAddress;
    private String distributorAddress;
    private String consumerAddress;

    // Other data
    @Column(name = "states") // Store states as a comma-separated string
    private String statesString;

    // Default constructor (required by JPA)
    public PharmaSubmittedView() {}

    public PharmaSubmittedView(List<String> transactionHashList, long timestamp,
                               String fromEntity, String toEntity,
                               String batchID, String productCode,
                               String productName, String productDescription,
                               String expirationDate, double manufacturerPrice,
                               double distributorPrice, int quantity,
                               String manufacturerAddress, String distributorAddress,
                               String consumerAddress, List<BigInteger> states) {
        this.transactionHashList = transactionHashList;
        this.timestamp = timestamp;
        this.fromEntity = fromEntity;
        this.toEntity = toEntity;
        this.batchID = batchID;
        this.productCode = productCode;
        this.productName = productName;
        this.productDescription = productDescription;
        this.expirationDate = expirationDate;
        this.manufacturerPrice = manufacturerPrice;
        this.distributorPrice = distributorPrice;
        this.quantity = quantity;
        this.manufacturerAddress = manufacturerAddress;
        this.distributorAddress = distributorAddress;
        this.consumerAddress = consumerAddress;
        setStates(states);
    }

    public String getBatchID() {
        return batchID;
    }

    public void setBatchID(String batchID) {
        this.batchID = batchID;
    }

    public List<String> getTransactionHashList() {
        return transactionHashList;
    }

    public void setTransactionHashList(List<String> transactionHashList) {
        this.transactionHashList = transactionHashList;
    }

    public void addTransactionHash(String transactionHash) {this.transactionHashList.add(transactionHash);}

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getFromEntity() {
        return fromEntity;
    }

    public void setFromEntity(String fromEntity) {
        this.fromEntity = fromEntity;
    }

    public String getToEntity() {
        return toEntity;
    }

    public void setToEntity(String toEntity) {
        this.toEntity = toEntity;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getProductDescription() {
        return productDescription;
    }

    public void setProductDescription(String productDescription) {
        this.productDescription = productDescription;
    }

    public String getExpirationDate() {
        return expirationDate;
    }

    public void setExpirationDate(String expirationDate) {
        this.expirationDate = expirationDate;
    }

    public double getManufacturerPrice() {
        return manufacturerPrice;
    }

    public void setManufacturerPrice(double manufacturerPrice) {
        this.manufacturerPrice = manufacturerPrice;
    }

    public double getDistributorPrice() {
        return distributorPrice;
    }

    public void setDistributorPrice(double distributorPrice) {
        this.distributorPrice = distributorPrice;
    }

    public String getManufacturerAddress() {
        return manufacturerAddress;
    }

    public void setManufacturerAddress(String manufacturerAddress) {
        this.manufacturerAddress = manufacturerAddress;
    }

    public String getDistributorAddress() {
        return distributorAddress;
    }

    public void setDistributorAddress(String distributorAddress) {
        this.distributorAddress = distributorAddress;
    }

    public String getReceiverAddress() {
        return consumerAddress;
    }

    public void setReceiverAddress(String receiverAddress) {
        this.consumerAddress = receiverAddress;
    }

    // Method to get states as a List<BigInteger>
    public List<BigInteger> getStates() {
        if (statesString == null || statesString.isEmpty()) {
            return Collections.emptyList(); // Return empty list if no states
        }
        return Arrays.stream(statesString.split(","))
                .map(String::trim) // Trim whitespace
                .map(BigInteger::new) // Convert each state to BigInteger
                .toList();
    }

    // Method to get states as a String
    public String getStateString() {
        return statesString;
    }

    // Method to set states from a List<BigInteger>
    public void setStates(List<BigInteger> states) {
        if (states == null || states.isEmpty()) {
            this.statesString = ""; // Set to empty if no states
        } else {
            StringBuilder sb = new StringBuilder();
            for (BigInteger state : states) {
                sb.append(state).append(","); // Build comma-separated string
            }
            // Remove the last comma
            this.statesString = sb.substring(0, sb.length() - 1);
        }
    }

    public void addState(State state) {
        if (state != null) {
            // Get the current states as a modifiable list
            List<BigInteger> currentStates = new ArrayList<>(getStates());

            // Add the new state value to the list
            currentStates.add(state.getValue());

            // Update the statesString with the new list of states
            setStates(currentStates); // Call setStates to update the statesString
        }
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}