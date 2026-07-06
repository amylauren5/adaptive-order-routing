package ict.um.pharmaceutical;

import com.fasterxml.jackson.databind.ObjectMapper;
import ict.um.pharmaceutical.coreapi.enums.State;
import ict.um.pharmaceutical.coreapi.events.*;
import ict.um.pharmaceutical.query_model.pharma_cached.PharmaCachedView;
import ict.um.pharmaceutical.query_model.pharma_cached.PharmaCachedViewRepository;
import ict.um.pharmaceutical.services.BlockchainWriteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.function.BiFunction;

@Component
public class EventListener {

    private static final Logger logger = LoggerFactory.getLogger(EventListener.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PharmaCachedViewRepository pharmaCachedViewRepository;
    private final BlockchainWriteService blockchainWriteService;

    @Autowired
    public EventListener(BlockchainWriteService blockchainWriteService,
                         PharmaCachedViewRepository pharmaCachedViewRepository) {
        this.blockchainWriteService = blockchainWriteService;
        this.pharmaCachedViewRepository = pharmaCachedViewRepository;
    }

    // ------------------- Upload Event -------------------

    @RabbitListener(queues = "blockchain.upload.queue")
    public void receiveUploadEventMessage(String message) {
        try {
            BatchUploadedEvent event = objectMapper.readValue(message, BatchUploadedEvent.class);

            blockchainWriteService
                    .addBatchTrackingOnBlockchain(event.getBatchID(), event.getDataHash(), event.getState())
                    .thenAccept(txHash -> savePharmaCachedView(event.getBatchID(), "Manufacturer", null));
        } catch (Exception e) {
            logDeserializationError("BatchUploadedEvent", message, e);
        }
    }

    // ------------------- Sell Event -------------------

    @RabbitListener(queues = "blockchain.sell.queue")
    public void receiveSellEventMessage(String message) {
        try {
            MedicineSoldEvent event = objectMapper.readValue(message, MedicineSoldEvent.class);

            handleEntityTransferEvent(
                    event.getBatchID(),
                    event.getToEntity(),
                    blockchainWriteService::updateBatchStateOnBlockchain,
                    event.getToEntity()
            );

        } catch (Exception e) {
            logDeserializationError("MedicineSoldEvent", message, e);
        }
    }

    // ------------------- Shipment Event -------------------

    @RabbitListener(queues = "blockchain.shipment.queue")
    public void receiveShipmentEventMessage(String message) {
        try {
            ShipmentReportedEvent event = objectMapper.readValue(message, ShipmentReportedEvent.class);

            handleEntityTransferEvent(
                    event.getBatchID(),
                    event.getToEntity(),
                    blockchainWriteService::updateBatchStateOnBlockchain,
                    event.getToEntity()
            );

        } catch (Exception e) {
            logDeserializationError("ShipmentReportedEvent", message, e);
        }
    }

    // ------------------- Delivery Event -------------------

    @RabbitListener(queues = "blockchain.delivery.queue")
    public void receiveDeliveryEventMessage(String message) {
        try {
            DeliveryReportedEvent event = objectMapper.readValue(message, DeliveryReportedEvent.class);

            handleEntityTransferEvent(
                    event.getBatchID(),
                    event.getToEntity(),
                    blockchainWriteService::updateBatchStateOnBlockchain,
                    event.getToEntity()
            );

        } catch (Exception e) {
            logDeserializationError("DeliveryReportedEvent", message, e);
        }
    }

    // ------------------- Cancel Event -------------------

    @RabbitListener(queues = "blockchain.cancel.queue")
    public void receiveCancelEventMessage(String message) {
        try {
            MedicineSaleCancelledEvent event = objectMapper.readValue(message, MedicineSaleCancelledEvent.class);

            blockchainWriteService
                    .updateBatchStateOnBlockchain(event.getBatchID(), State.CANCELLED_BY_CONSUMER)
                    .thenAccept(txHash ->
                            savePharmaCachedView(event.getBatchID(), "Distributor", "Consumer")
                    );
        } catch (Exception e) {
            logDeserializationError("MedicineSaleCancelledEvent", message, e);
        }
    }

    // ------------------- Utility Methods -------------------

    private void savePharmaCachedView(String batchId, String from, String to) {
        long timestamp = Instant.now().toEpochMilli();
        PharmaCachedView view = new PharmaCachedView(batchId, from, to, timestamp);
        pharmaCachedViewRepository.save(view);
        logger.info("PharmaSubmittedView stored in DB: {}", view);
    }

    private void logDeserializationError(String type, String message, Exception e) {
        logger.error("Failed to deserialize {}: {}", type, message, e);
    }

    private void handleEntityTransferEvent(
            String batchId,
            String toEntity,
            BiFunction<String, State, java.util.concurrent.CompletableFuture<String>> blockchainUpdater,
            String targetEntity
    ) {
        Map<String, EntityTransferState> transferMap = Map.of(
                "Distributor", new EntityTransferState(State.valueOf("SHIPPED_TO_DISTRIBUTOR"), "Manufacturer"),
                "Consumer", new EntityTransferState(State.valueOf("SHIPPED_TO_CONSUMER"), "Distributor")
        );

        EntityTransferState state = transferMap.get(toEntity);
        if (state != null) {
            blockchainUpdater.apply(batchId, state.state())
                    .thenAccept(txHash -> savePharmaCachedView(batchId, state.fromEntity(), targetEntity));
        } else {
            logger.warn("Received unknown entity: {}", toEntity);
        }
    }

    private record EntityTransferState(State state, String fromEntity) {}
}
