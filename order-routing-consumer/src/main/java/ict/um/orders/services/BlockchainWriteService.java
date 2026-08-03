package ict.um.orders.services;

import ict.um.orders.web3j.OrderLifecycleContract;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.gas.DefaultGasProvider;

@Service
public class BlockchainWriteService {

    private final OrderLifecycleContract orderLifecycleContract;

    @Autowired
    public BlockchainWriteService(
            @Value("${web3.provider}") String web3Provider,
            @Value("${private.key}") String privateKey,
            @Value("${contract.address}") String contractAddress) {

        if (web3Provider == null || web3Provider.isBlank()) {
            throw new IllegalArgumentException("Web3 provider is not configured.");
        }

        if (privateKey == null || privateKey.isEmpty()) {
            throw new IllegalArgumentException("Private key is not set. Please check your application.properties.");
        }

        if (contractAddress == null || contractAddress.isBlank()) {
            throw new IllegalArgumentException("Contract address is not configured.");
        }

        Web3j web3j = Web3j.build(new HttpService(web3Provider));
        Credentials credentials = Credentials.create(privateKey);

        this.orderLifecycleContract = OrderLifecycleContract.load(
                    contractAddress,
                    web3j,
                    credentials,
                    new DefaultGasProvider()
            );
    }

    // CREATE ORDER
    public synchronized String createOrderOnBlockchain(
            String orderId,
            String hash
    ) {
        try {
            return orderLifecycleContract
                    .send_createOrder(orderId, hash)
                    .send()
                    .getTransactionHash();
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to create order " + orderId + " on the blockchain",
                    exception
            );
        }
    }

    // APPROVE ORDER
    public synchronized String approveOrderOnBlockchain(String orderId) {
        try {
            return orderLifecycleContract
                    .send_approveOrder(orderId)
                    .send()
                    .getTransactionHash();
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to approve order " + orderId + " on the blockchain",
                    exception
            );
        }
    }

    // DISPATCH ORDER
    public synchronized String dispatchOrderOnBlockchain(String orderId) {
        try {
            return orderLifecycleContract
                    .send_dispatchOrder(orderId)
                    .send()
                    .getTransactionHash();
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to dispatch order " + orderId + " on the blockchain",
                    exception
            );
        }
    }

    // COMPLETE ORDER
    public synchronized String completeOrderOnBlockchain(String orderId) {
        try {
            return orderLifecycleContract
                    .send_completeOrder(orderId)
                    .send()
                    .getTransactionHash();
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to complete order " + orderId + " on the blockchain",
                    exception
            );
        }
    }

    // CANCEL ORDER
    public synchronized String cancelOrderOnBlockchain(String orderId, String reason) {
        try {
            return orderLifecycleContract
                    .send_cancelOrder(orderId, reason)
                    .send()
                    .getTransactionHash();
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to cancel order " + orderId + " on the blockchain",
                    exception
            );
        }
    }
}
