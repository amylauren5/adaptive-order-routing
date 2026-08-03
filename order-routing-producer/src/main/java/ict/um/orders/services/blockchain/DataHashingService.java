package ict.um.orders.services.blockchain;

import ict.um.orders.core_api.commands.CreateOrderCommand;
import ict.um.orders.query_model.OrderRoutingView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class DataHashingService {

    private static final Logger logger =
            LoggerFactory.getLogger(DataHashingService.class);

    private final BlockchainReadService blockchainReadService;

    public DataHashingService(
            BlockchainReadService blockchainReadService
    ) {
        this.blockchainReadService = blockchainReadService;
    }

    // --- HASH FOR CREATE ORDER ---

    public String computeInitialDataHash(CreateOrderCommand command) {
        String data = buildHashInput(
                command.getOrderId(),
                command.getCustomerId(),
                command.getCategory(),
                command.getOrderValue(),
                command.getItemCount(),
                command.getTimestamp()
        );

        return hashString(data);
    }

    // --- HASH RECONSTRUCTION FROM OFF-CHAIN VIEW ---

    public String reconstructDataHash(OrderRoutingView view) {
        String data = buildHashInput(
                view.getOrderId(),
                view.getCustomerId(),
                view.getCategory(),
                view.getOrderValue(),
                view.getItemCount(),
                view.getCreatedAt()
        );

        return hashString(data);
    }

    // --- VERIFY OFF-CHAIN DATA AGAINST BLOCKCHAIN ---

    public CompletableFuture<Map<String, Boolean>> verifyDataHash(
            OrderRoutingView view
    ) {
        String reconstructedHash = reconstructDataHash(view);

        return blockchainReadService
                .getOrderHash(view.getOrderId())
                .thenApply(blockchainHash -> {
                    if (blockchainHash == null || blockchainHash.isBlank()) {
                        logger.warn(
                                "Blockchain hash missing for order {}",
                                view.getOrderId()
                        );

                        return Map.of(
                                "blockchain_hash_missing",
                                false
                        );
                    }

                    boolean matches =
                            blockchainHash.equalsIgnoreCase(reconstructedHash);

                    if (matches) {
                        logger.info(
                                "Hash match for order {}",
                                view.getOrderId()
                        );
                    } else {
                        logger.warn(
                                "Hash mismatch for order {}: "
                                        + "reconstructed={}, blockchain={}",
                                view.getOrderId(),
                                reconstructedHash,
                                blockchainHash
                        );
                    }

                    return Map.of("hash_match", matches);
                })
                .exceptionally(exception -> {
                    logger.error(
                            "Failed to verify hash for order {}",
                            view.getOrderId(),
                            exception
                    );

                    return Map.of("verification_error", false);
                });
    }

    // --- HASH INPUT ---

    private String buildHashInput(
            String orderId,
            String customerId,
            String category,
            double orderValue,
            int itemCount,
            long timestamp
    ) {
        /*
         * Delimiters prevent ambiguous concatenation, for example:
         * "12" + "3" and "1" + "23".
         */
        return String.join(
                "|",
                orderId,
                customerId,
                category,
                Double.toString(orderValue),
                Integer.toString(itemCount),
                Long.toString(timestamp)
        );
    }

    // --- INTERNAL HASHING ---

    private String hashString(String input) {
        return hashBytes(input.getBytes(StandardCharsets.UTF_8));
    }

    private String hashBytes(byte[] input) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(input);

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is not available",
                    exception
            );
        }
    }
}