package ict.um.orders.services;

import ict.um.orders.web3j_wrappers.OrderLifecycleContract;
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
    private final OrderLifecycleContract orderLifecycleContract;

    @Autowired
    public BlockchainWriteService(
            @Value("${web3.provider}") String web3Provider,
            @Value("${private.key}") String privateKey,
            @Value("${contract.address}") String contractAddress) {

        Web3j web3j = Web3j.build(new HttpService(web3Provider));

        if (privateKey == null || privateKey.isEmpty()) {
            throw new IllegalArgumentException("Private key is not set. Please check your application.properties.");
        }

        Credentials credentials = Credentials.create(privateKey);

        try {
            this.orderLifecycleContract = OrderLifecycleContract.load(
                    contractAddress,
                    web3j,
                    credentials,
                    new DefaultGasProvider()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to load OrderLifecycleContract: ", e);
        }
    }

    // --- CREATE ORDER ---
    public CompletableFuture<String> createOrderOnBlockchain(String orderId, String hash) {
        CompletableFuture<String> future = new CompletableFuture<>();

        orderLifecycleContract.send_createOrder(orderId, hash)
                .sendAsync()
                .thenAccept(receipt -> future.complete(receipt.getTransactionHash()))
                .exceptionally(ex -> {
                    future.completeExceptionally(ex);
                    return null;
                });

        return future;
    }

    // --- APPROVE ORDER ---
    public CompletableFuture<String> approveOrderOnBlockchain(String orderId) {
        CompletableFuture<String> future = new CompletableFuture<>();

        orderLifecycleContract.send_approveOrder(orderId)
                .sendAsync()
                .thenAccept(receipt -> future.complete(receipt.getTransactionHash()))
                .exceptionally(ex -> {
                    future.completeExceptionally(ex);
                    return null;
                });

        return future;
    }

    // --- DISPATCH ORDER ---
    public CompletableFuture<String> dispatchOrderOnBlockchain(String orderId) {
        CompletableFuture<String> future = new CompletableFuture<>();

        orderLifecycleContract.send_dispatchOrder(orderId)
                .sendAsync()
                .thenAccept(receipt -> future.complete(receipt.getTransactionHash()))
                .exceptionally(ex -> {
                    future.completeExceptionally(ex);
                    return null;
                });

        return future;
    }

    // --- COMPLETE ORDER ---
    public CompletableFuture<String> completeOrderOnBlockchain(String orderId) {
        CompletableFuture<String> future = new CompletableFuture<>();

        orderLifecycleContract.send_completeOrder(orderId)
                .sendAsync()
                .thenAccept(receipt -> future.complete(receipt.getTransactionHash()))
                .exceptionally(ex -> {
                    future.completeExceptionally(ex);
                    return null;
                });

        return future;
    }

    // --- CANCEL ORDER ---
    public CompletableFuture<String> cancelOrderOnBlockchain(String orderId, String reason) {
        CompletableFuture<String> future = new CompletableFuture<>();

        orderLifecycleContract.send_cancelOrder(orderId, reason)
                .sendAsync()
                .thenAccept(receipt -> future.complete(receipt.getTransactionHash()))
                .exceptionally(ex -> {
                    future.completeExceptionally(ex);
                    return null;
                });

        return future;
    }
}
