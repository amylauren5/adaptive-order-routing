package ict.um.orders.services;

import ict.um.orders.web3j.OrderLifecycleContract;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.gas.DefaultGasProvider;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class BlockchainReadService {

    private static final Logger logger = LoggerFactory.getLogger(BlockchainReadService.class);

    private final Web3j web3j;
    private final Credentials credentials;
    private final String contractAddress;

    private OrderLifecycleContract orderContract;

    @Autowired
    public BlockchainReadService(
            @Value("${web3.provider}") String web3Provider,
            @Value("${private.key}") String privateKey,
            @Value("${contract.address}") String contractAddress) {

        this.web3j = Web3j.build(new HttpService(web3Provider));
        this.credentials = Credentials.create(privateKey);
        this.contractAddress = contractAddress;
    }

    @PostConstruct
    public void initContract() {
        try {
            this.orderContract = OrderLifecycleContract.load(
                    contractAddress,
                    web3j,
                    credentials,
                    new DefaultGasProvider()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize OrderLifecycleContract", e);
        }
    }

    // --- READ ORDER HASH ---
    public CompletableFuture<String> getOrderHash(String orderId) {
        return orderContract.call_getOrderHash(orderId).sendAsync();
    }

    // --- READ LATEST STATE ---
    public CompletableFuture<BigInteger> getLatestState(String orderId) {
        return orderContract.call_getLatestState(orderId).sendAsync();
    }

    // --- READ FULL STATE HISTORY ---
    public CompletableFuture<List> getOrderStates(String orderId) {
        return orderContract.call_getOrderStates(orderId).sendAsync();
    }
}