package ict.um.orders.services.blockchain;

import ict.um.orders.core_api.commands.CreateOrderCommand;
import ict.um.orders.query_model.order_submitted.OrderSubmittedView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

@Service
public class DataHashingService {

    private static final Logger logger = LoggerFactory.getLogger(DataHashingService.class);

    private final BlockchainReadService blockchainReadService;

    @Autowired
    public DataHashingService(BlockchainReadService blockchainReadService) {
        this.blockchainReadService = blockchainReadService;
    }

    // --- HASH FOR CREATE ORDER ---
    public String computeInitialDataHash(CreateOrderCommand command) {

        String data =
                command.getOrderId() +
                        command.getCustomerId() +
                        command.getCategory() +
                        command.getOrderValue() +
                        command.getItemCount() +
                        command.getTimestamp() +
                        command.getPriority() +
                        command.getSequenceNumber();

        return hashString(data);
    }

    // --- HASH FOR PROJECTOR RECONSTRUCTION ---
    public String reconstructDataHash(OrderSubmittedView view) {

        String data =
                view.getOrderId() +
                        view.getCustomerId() +
                        view.getCategory() +
                        view.getOrderValue() +
                        view.getItemCount() +
                        view.getTimestamp() +
                        view.getPriority() +
                        view.getSequenceNumber();

        return hashString(data);
    }

    // --- VERIFY HASH AGAINST BLOCKCHAIN ---
    public CompletableFuture<Map<String, Boolean>> verifyDataHash(OrderSubmittedView view) {

        String reconstructed = reconstructDataHash(view);

        return blockchainReadService.getOrderHash(view.getOrderId())
                .thenApply(blockchainHash -> {

                    Map<String, Boolean> result = new HashMap<>();

                    if (blockchainHash == null || blockchainHash.isEmpty()) {
                        logger.warn("Blockchain hash empty for order {}", view.getOrderId());
                        result.put("blockchain_hash_missing", false);
                        return result;
                    }

                    boolean matches = blockchainHash.equals(reconstructed);

                    if (matches) {
                        logger.info("Hash match for order {}", view.getOrderId());
                    } else {
                        logger.warn("Hash mismatch for order {}: reconstructed={}, blockchain={}",
                                view.getOrderId(), reconstructed, blockchainHash);
                    }

                    result.put("hash_match", matches);
                    return result;
                })
                .exceptionally(ex -> {
                    logger.error("Error verifying hash for order {}: {}", view.getOrderId(), ex.getMessage());
                    Map<String, Boolean> error = new HashMap<>();
                    error.put("error", false);
                    return error;
                });
    }

    // --- INTERNAL HASHING ---
    private String hashString(String input) {
        return hashBytes(input.getBytes(StandardCharsets.UTF_8));
    }

    private String hashBytes(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
