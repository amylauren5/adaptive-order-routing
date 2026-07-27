package ict.um.orders;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ict.um.orders.core_api.enums.OrderStatus;
import ict.um.orders.core_api.events.OrderApprovedEvent;
import ict.um.orders.core_api.events.OrderCancelledEvent;
import ict.um.orders.core_api.events.OrderCompletedEvent;
import ict.um.orders.core_api.events.OrderCreatedEvent;
import ict.um.orders.core_api.events.OrderDispatchedEvent;
import ict.um.orders.core_api.messaging.RoutedEventMessage;
import ict.um.orders.query_model.order_cached.OrderCachedView;
import ict.um.orders.query_model.order_cached.OrderCachedViewRepository;
import ict.um.orders.services.BlockchainWriteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static ict.um.orders.core_api.config.QueueNames.HIGH;
import static ict.um.orders.core_api.config.QueueNames.LOW;
import static ict.um.orders.core_api.config.QueueNames.MEDIUM;

@Component
public class EventListener {

    private static final Logger logger =
            LoggerFactory.getLogger(EventListener.class);

    private final ObjectMapper objectMapper;
    private final OrderCachedViewRepository orderCachedViewRepository;
    private final BlockchainWriteService blockchainWriteService;

    public EventListener(
            ObjectMapper objectMapper,
            BlockchainWriteService blockchainWriteService,
            OrderCachedViewRepository orderCachedViewRepository
    ) {
        this.objectMapper = objectMapper;
        this.blockchainWriteService = blockchainWriteService;
        this.orderCachedViewRepository = orderCachedViewRepository;
    }

    // ------------------- Queue listeners -------------------

    @RabbitListener(queues = LOW)
    public void receiveLowPriority(String message) {
        receive(message, LOW);
    }

    @RabbitListener(queues = MEDIUM)
    public void receiveMediumPriority(String message) {
        receive(message, MEDIUM);
    }

    @RabbitListener(queues = HIGH)
    public void receiveHighPriority(String message) {
        receive(message, HIGH);
    }

    // ------------------- Envelope dispatch -------------------

    private void receive(String message, String queue) {
        try {
            RoutedEventMessage routedMessage =
                    objectMapper.readValue(message, RoutedEventMessage.class);

            if (routedMessage.getEventType() == null) {
                throw new IllegalArgumentException(
                        "Routed message does not contain an event type"
                );
            }

            if (routedMessage.getPayload() == null
                    || routedMessage.getPayload().isBlank()) {
                throw new IllegalArgumentException(
                        "Routed message does not contain a payload"
                );
            }

            logger.info(
                    "Received {} from queue {}",
                    routedMessage.getEventType(),
                    queue
            );

            switch (routedMessage.getEventType()) {
                case ORDER_CREATED ->
                        handleOrderCreated(routedMessage.getPayload());

                case ORDER_APPROVED ->
                        handleOrderApproved(routedMessage.getPayload());

                case ORDER_DISPATCHED ->
                        handleOrderDispatched(routedMessage.getPayload());

                case ORDER_COMPLETED ->
                        handleOrderCompleted(routedMessage.getPayload());

                case ORDER_CANCELLED ->
                        handleOrderCancelled(routedMessage.getPayload());
            }

        } catch (JsonProcessingException exception) {
            logger.error(
                    "Failed to deserialize routed message from queue {}: {}",
                    queue,
                    message,
                    exception
            );

        } catch (Exception exception) {
            logger.error(
                    "Failed to process message from queue {}: {}",
                    queue,
                    message,
                    exception
            );
        }
    }

    // ------------------- Event handlers -------------------

    private void handleOrderCreated(String payload)
            throws JsonProcessingException {

        OrderCreatedEvent event =
                objectMapper.readValue(payload, OrderCreatedEvent.class);

        blockchainWriteService
                .createOrderOnBlockchain(
                        event.getOrderId(),
                        event.getDataHash()
                )
                .thenAccept(transactionHash -> {
                    saveOrderCachedView(
                            event.getOrderId(),
                            OrderStatus.CREATED,
                            event.getCategory(),
                            event.getOrderValue(),
                            event.getItemCount(),
                            event.getTimestamp()
                    );

                    logger.info(
                            "Order {} created on blockchain with transaction {}",
                            event.getOrderId(),
                            transactionHash
                    );
                })
                .exceptionally(exception -> {
                    logger.error(
                            "Failed to create order {} on blockchain",
                            event.getOrderId(),
                            exception
                    );
                    return null;
                });
    }

    private void handleOrderApproved(String payload)
            throws JsonProcessingException {

        OrderApprovedEvent event =
                objectMapper.readValue(payload, OrderApprovedEvent.class);

        blockchainWriteService
                .approveOrderOnBlockchain(event.getOrderId())
                .thenAccept(transactionHash -> {
                    updateStatus(
                            event.getOrderId(),
                            OrderStatus.APPROVED,
                            event.getTimestamp()
                    );

                    logger.info(
                            "Order {} approved on blockchain with transaction {}",
                            event.getOrderId(),
                            transactionHash
                    );
                })
                .exceptionally(exception -> {
                    logger.error(
                            "Failed to approve order {} on blockchain",
                            event.getOrderId(),
                            exception
                    );
                    return null;
                });
    }

    private void handleOrderDispatched(String payload)
            throws JsonProcessingException {

        OrderDispatchedEvent event =
                objectMapper.readValue(payload, OrderDispatchedEvent.class);

        blockchainWriteService
                .dispatchOrderOnBlockchain(event.getOrderId())
                .thenAccept(transactionHash -> {
                    updateStatus(
                            event.getOrderId(),
                            OrderStatus.DISPATCHED,
                            event.getTimestamp()
                    );

                    logger.info(
                            "Order {} dispatched on blockchain with transaction {}",
                            event.getOrderId(),
                            transactionHash
                    );
                })
                .exceptionally(exception -> {
                    logger.error(
                            "Failed to dispatch order {} on blockchain",
                            event.getOrderId(),
                            exception
                    );
                    return null;
                });
    }

    private void handleOrderCompleted(String payload)
            throws JsonProcessingException {

        OrderCompletedEvent event =
                objectMapper.readValue(payload, OrderCompletedEvent.class);

        blockchainWriteService
                .completeOrderOnBlockchain(event.getOrderId())
                .thenAccept(transactionHash -> {
                    updateStatus(
                            event.getOrderId(),
                            OrderStatus.COMPLETED,
                            event.getTimestamp()
                    );

                    logger.info(
                            "Order {} completed on blockchain with transaction {}",
                            event.getOrderId(),
                            transactionHash
                    );
                })
                .exceptionally(exception -> {
                    logger.error(
                            "Failed to complete order {} on blockchain",
                            event.getOrderId(),
                            exception
                    );
                    return null;
                });
    }

    private void handleOrderCancelled(String payload)
            throws JsonProcessingException {

        OrderCancelledEvent event =
                objectMapper.readValue(payload, OrderCancelledEvent.class);

        blockchainWriteService
                .cancelOrderOnBlockchain(
                        event.getOrderId(),
                        event.getReason()
                )
                .thenAccept(transactionHash -> {
                    updateStatus(
                            event.getOrderId(),
                            OrderStatus.CANCELLED,
                            event.getTimestamp()
                    );

                    logger.info(
                            "Order {} cancelled on blockchain with transaction {}",
                            event.getOrderId(),
                            transactionHash
                    );
                })
                .exceptionally(exception -> {
                    logger.error(
                            "Failed to cancel order {} on blockchain",
                            event.getOrderId(),
                            exception
                    );
                    return null;
                });
    }

    // ------------------- Projection updates -------------------

    private void saveOrderCachedView(
            String orderId,
            OrderStatus status,
            String category,
            double orderValue,
            int itemCount,
            long eventTimestamp
    ) {
        OrderCachedView view = new OrderCachedView(
                orderId,
                status.name(),
                category,
                orderValue,
                itemCount,
                eventTimestamp
        );

        orderCachedViewRepository.save(view);

        logger.info(
                "OrderCachedView stored for order {} with status {}",
                orderId,
                status
        );
    }

    private void updateStatus(
            String orderId,
            OrderStatus newStatus,
            long eventTimestamp
    ) {
        OrderCachedView view = orderCachedViewRepository.findById(orderId)
                .orElseThrow(() -> new IllegalStateException(
                        "Cached view missing for orderId " + orderId
                ));

        view.setStatus(newStatus.name());
        view.setLastEventTimestamp(eventTimestamp);

        orderCachedViewRepository.save(view);

        logger.info(
                "OrderCachedView updated for order {} to status {}",
                orderId,
                newStatus
        );
    }
}