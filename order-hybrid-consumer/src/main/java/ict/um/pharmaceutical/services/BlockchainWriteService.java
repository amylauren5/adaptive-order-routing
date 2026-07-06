package ict.um.pharmaceutical.services;

import ict.um.pharmaceutical.coreapi.enums.State;
import ict.um.pharmaceutical.web3j_wrappers.PharmaTrackingContract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.gas.DefaultGasProvider;

import java.util.concurrent.CompletableFuture;

@Service
public class BlockchainWriteService {

    private static final Logger logger = LoggerFactory.getLogger(BlockchainWriteService.class);
    private final PharmaTrackingContract pharmaTrackingContract;

    @Autowired
    public BlockchainWriteService(@Value("${web3.provider}") String web3Provider,
                                  @Value("${private.key}") String privateKey,
                                  @Value("${contract.address}") String contractAddress) {
        // Initialise Web3j to connect to the provider
        Web3j web3j = Web3j.build(new HttpService(web3Provider));

        // Load credentials
        if (privateKey == null || privateKey.isEmpty()) {
            throw new IllegalArgumentException("Private key is not set. Please check your application.properties.");
        }
        Credentials credentials = Credentials.create(privateKey);

        try {
            this.pharmaTrackingContract = PharmaTrackingContract.load(contractAddress, web3j, credentials, new DefaultGasProvider());
        } catch (Exception e) {
            throw new RuntimeException("Failed to load contract: ", e);
        }
    }

    public CompletableFuture<String> addBatchTrackingOnBlockchain(String batchId, String dataHash, State state) {
        CompletableFuture<String> future = new CompletableFuture<>();

        // Complete the future with the TransactionReceipt
        pharmaTrackingContract.send_addBatchTracking(batchId, dataHash, state.getValue())
                .sendAsync()
                .thenAccept(transactionReceipt -> future.complete(transactionReceipt.getTransactionHash()))
                .exceptionally(ex -> {
                    // If an exception occurs, complete the future exceptionally
                    future.completeExceptionally(ex);
                    return null;
                });

        return future;
    }

    public CompletableFuture<String> updateBatchStateOnBlockchain(String batchID, State newState) {
        CompletableFuture<String> future = new CompletableFuture<>();

        // Complete the future with the TransactionReceipt
        pharmaTrackingContract.send_updateBatchState(batchID,newState.getValue())
                .sendAsync()
                .thenAccept(transactionReceipt -> future.complete(transactionReceipt.getTransactionHash()))
                .exceptionally(ex -> {
                    // If an exception occurs, complete the future exceptionally
                    future.completeExceptionally(ex);
                    return null;
                });

        return future;
    }

}