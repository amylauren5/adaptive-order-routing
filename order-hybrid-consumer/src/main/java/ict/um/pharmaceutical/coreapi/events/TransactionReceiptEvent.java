package ict.um.pharmaceutical.coreapi.events;

public class TransactionReceiptEvent {
    private final String transactionHash;
    private final String batchID;

    public TransactionReceiptEvent(String transactionHash, String batchID) {
        this.transactionHash = transactionHash;
        this.batchID = batchID;
    }

    public String getTransactionHash() {
        return transactionHash;
    }

    public String getBatchID() {
        return batchID;
    }
}
