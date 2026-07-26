package ict.um.orders;

import com.fasterxml.jackson.databind.ObjectMapper;
import ict.um.orders.core_api.events.*;
import ict.um.orders.core_api.enums.OrderStatus;
import ict.um.orders.query_model.order_cached.OrderCachedView;
import ict.um.orders.query_model.order_cached.OrderCachedViewRepository;
import ict.um.orders.services.BlockchainWriteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class EventListener {

    private static final Logger logger = LoggerFactory.getLogger(EventListener.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OrderCachedViewRepository orderCachedViewRepository;
    private final BlockchainWriteService blockchainWriteService;

    // Priority queues
    private static final String HIGH_PRIORITY_QUEUE = "priority.high";
    private static final String MEDIUM_PRIORITY_QUEUE = "priority.medium";
    private static final String LOW_PRIORITY_QUEUE = "priority.low"; // fixed typo

    @Autowired
    public EventListener(BlockchainWriteService blockchainWriteService,
                         OrderCachedViewRepository orderCachedViewRepository) {
        this.blockchainWriteService = blockchainWriteService;
        this.orderCachedViewRepository = orderCachedViewRepository;
    }

    // ------------------- ORDER CREATED → LOW PRIORITY -------------------

    @RabbitListener(queues = LOW_PRIORITY_QUEUE)
    public void receiveOrderCreated(String message) {
        try {
            OrderCreatedEvent event = objectMapper.readValue(message, OrderCreatedEvent.class);

            blockchainWriteService.createOrderOnBlockchain(event.getOrderId(), event.getDataHash())
                    .thenAccept(txHash -> saveOrderCachedView(
                            event.getOrderId(),
                            OrderStatus.CREATED.name(),
                            event.getCategory(),
                            event.getOrderValue(),
                            event.getItemCount()
                    ));

        } catch (Exception e) {
            logDeserializationError("OrderCreatedEvent", message, e);
        }
    }

    // ------------------- ORDER APPROVED → MEDIUM PRIORITY -------------------

    @RabbitListener(queues = MEDIUM_PRIORITY_QUEUE)
    public void receiveOrderApproved(String message) {
        try {
            OrderApprovedEvent event = objectMapper.readValue(message, OrderApprovedEvent.class);

            blockchainWriteService.approveOrderOnBlockchain(event.getOrderId())
                    .thenAccept(txHash -> updateStatus(event.getOrderId(), OrderStatus.APPROVED));

        } catch (Exception e) {
            logDeserializationError("OrderApprovedEvent", message, e);
        }
    }

    // ------------------- ORDER DISPATCHED → MEDIUM PRIORITY -------------------

    @RabbitListener(queues = MEDIUM_PRIORITY_QUEUE)
    public void receiveOrderDispatched(String message) {
        try {
            OrderDispatchedEvent event = objectMapper.readValue(message, OrderDispatchedEvent.class);

            blockchainWriteService.dispatchOrderOnBlockchain(event.getOrderId())
                    .thenAccept(txHash -> updateStatus(event.getOrderId(), OrderStatus.DISPATCHED));

        } catch (Exception e) {
            logDeserializationError("OrderDispatchedEvent", message, e);
        }
    }

    // ------------------- ORDER COMPLETED → HIGH PRIORITY -------------------

    @RabbitListener(queues = HIGH_PRIORITY_QUEUE)
    public void receiveOrderCompleted(String message) {
        try {
            OrderCompletedEvent event = objectMapper.readValue(message, OrderCompletedEvent.class);

            blockchainWriteService.completeOrderOnBlockchain(event.getOrderId())
                    .thenAccept(txHash -> updateStatus(event.getOrderId(), OrderStatus.COMPLETED));

        } catch (Exception e) {
            logDeserializationError("OrderCompletedEvent", message, e);
        }
    }

    // ------------------- ORDER CANCELLED → HIGH PRIORITY -------------------

    @RabbitListener(queues = HIGH_PRIORITY_QUEUE)
    public void receiveOrderCancelled(String message) {
        try {
            OrderCancelledEvent event = objectMapper.readValue(message, OrderCancelledEvent.class);

            blockchainWriteService.cancelOrderOnBlockchain(event.getOrderId(), event.getReason())
                    .thenAccept(txHash -> updateStatus(event.getOrderId(), OrderStatus.CANCELLED));

        } catch (Exception e) {
            logDeserializationError("OrderCancelledEvent", message, e);
        }
    }

    // ------------------- Utility Methods -------------------

    private void saveOrderCachedView(String orderId,
                                     String status,
                                     String category,
                                     double orderValue,
                                     int itemCount) {

        long timestamp = Instant.now().toEpochMilli();

        OrderCachedView view = new OrderCachedView(
                orderId,
                status,
                category,
                orderValue,
                itemCount,
                timestamp
        );

        orderCachedViewRepository.save(view);
        logger.info("OrderCachedView stored: {}", view);
    }

    private void updateStatus(String orderId, OrderStatus newStatus) {
        OrderCachedView view = orderCachedViewRepository.findById(orderId)
                .orElseThrow(() -> new IllegalStateException("Cached view missing for orderId " + orderId));

        view.setStatus(newStatus.name());
        view.setLastEventTimestamp(Instant.now().toEpochMilli());

        orderCachedViewRepository.save(view);
        logger.info("OrderCachedView updated: {}", view);
    }

    private void logDeserializationError(String type, String message, Exception e) {
        logger.error("Failed to deserialize {}: {}", type, message, e);
    }
}