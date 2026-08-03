package ict.um.orders.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ict.um.orders.core_api.enums.OrderStatus;
import ict.um.orders.core_api.events.OrderApprovedEvent;
import ict.um.orders.core_api.events.OrderCancelledEvent;
import ict.um.orders.core_api.events.OrderCompletedEvent;
import ict.um.orders.core_api.events.OrderCreatedEvent;
import ict.um.orders.core_api.events.OrderDispatchedEvent;
import ict.um.orders.core_api.messaging.RoutedEventMessage;
import ict.um.orders.exceptions.OutOfOrderEventException;
import ict.um.orders.query_model.orders.OrderView;
import ict.um.orders.query_model.orders.OrderViewRepository;
import ict.um.orders.query_model.pending_orders.PendingOrdersRepository;
import ict.um.orders.query_model.pending_orders.PendingOrdersView;
import ict.um.orders.services.BlockchainWriteService;
import ict.um.orders.training.TrainingOutcomeLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static ict.um.orders.core_api.config.QueueNames.QUEUE_1;
import static ict.um.orders.core_api.config.QueueNames.QUEUE_2;
import static ict.um.orders.core_api.config.QueueNames.QUEUE_3;

@Component
public class EventListener {

    private static final Logger logger =
            LoggerFactory.getLogger(EventListener.class);

    private final ObjectMapper objectMapper;
    private final OrderViewRepository orderViewRepository;
    private final PendingOrdersRepository pendingOrdersRepository;
    private final BlockchainWriteService blockchainWriteService;
    private final TrainingOutcomeLogger trainingOutcomeLogger;

    public EventListener(
            ObjectMapper objectMapper,
            BlockchainWriteService blockchainWriteService,
            OrderViewRepository orderViewRepository,
            PendingOrdersRepository pendingOrdersRepository,
            TrainingOutcomeLogger trainingOutcomeLogger
    ) {
        this.objectMapper = objectMapper;
        this.blockchainWriteService = blockchainWriteService;
        this.orderViewRepository = orderViewRepository;
        this.pendingOrdersRepository = pendingOrdersRepository;
        this.trainingOutcomeLogger = trainingOutcomeLogger;
    }

    // ------------------- Queue listeners -------------------

    @RabbitListener(queues = QUEUE_1)
    public void receiveProcessingQueue1(String message) {
        receive(message, QUEUE_1);
    }

    @RabbitListener(queues = QUEUE_2)
    public void receiveProcessingQueue2(String message) {
        receive(message, QUEUE_2);
    }

    @RabbitListener(queues = QUEUE_3)
    public void receiveProcessingQueue3(String message) {
        receive(message, QUEUE_3);
    }

    // ------------------- Message processing -------------------

    private void receive(String message, String queue) {
        try {
            processMessage(message, queue);
        } catch (Exception exception) {
            logger.error(
                    "Failed to process message from queue {}: {}",
                    queue,
                    message,
                    exception
            );

            throw new IllegalStateException(
                    "Failed to process message from queue " + queue,
                    exception
            );
        }
    }

    private boolean processMessage(
            String message,
            String queue
    ) throws JsonProcessingException {

        RoutedEventMessage routedMessage =
                objectMapper.readValue(
                        message,
                        RoutedEventMessage.class
                );

        validate(routedMessage);

        long consumerStartedAt = System.currentTimeMillis();

        logger.info(
                "Received {} from queue {}",
                routedMessage.getEventType(),
                queue
        );

        boolean processed = switch (routedMessage.getEventType()) {
            case ORDER_CREATED ->
                    handleOrderCreated(routedMessage);

            case ORDER_APPROVED ->
                    handleOrderApproved(
                            routedMessage,
                            queue,
                            message
                    );

            case ORDER_DISPATCHED ->
                    handleOrderDispatched(
                            routedMessage,
                            queue,
                            message
                    );

            case ORDER_COMPLETED ->
                    handleOrderCompleted(
                            routedMessage,
                            queue,
                            message
                    );

            case ORDER_CANCELLED ->
                    handleOrderCancelled(
                            routedMessage,
                            queue,
                            message
                    );
        };

        if (processed) {
            trainingOutcomeLogger.logOutcome(
                    routedMessage.getRoutingDecisionId(),
                    routedMessage.getSelectedQueue(),
                    routedMessage.getPublishedAt(),
                    consumerStartedAt
            );

            logger.info(
                    "Successfully processed {} from queue {}",
                    routedMessage.getEventType(),
                    queue
            );
        }

        return processed;
    }

    private void validate(RoutedEventMessage routedMessage) {
        if (routedMessage.getRoutingDecisionId() == null
                || routedMessage.getRoutingDecisionId().isBlank()) {
            throw new IllegalArgumentException(
                    "Routed message does not contain a routing decision ID"
            );
        }

        if (routedMessage.getSelectedQueue() == null
                || routedMessage.getSelectedQueue().isBlank()) {
            throw new IllegalArgumentException(
                    "Routed message does not contain a selected queue"
            );
        }

        if (routedMessage.getPublishedAt() <= 0L) {
            throw new IllegalArgumentException(
                    "Routed message contains an invalid publication timestamp"
            );
        }

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
    }

    // ------------------- Event handlers -------------------

    private boolean handleOrderCreated(
            RoutedEventMessage routedMessage
    ) throws JsonProcessingException {

        OrderCreatedEvent event =
                objectMapper.readValue(
                        routedMessage.getPayload(),
                        OrderCreatedEvent.class
                );

        if (event.getSequenceNumber() != 0) {
            throw new OutOfOrderEventException(
                    event.getOrderId(),
                    0,
                    event.getSequenceNumber()
            );
        }

        /*
         * A redelivered creation event must not submit the same
         * blockchain transaction again.
         */
        if (orderViewRepository.existsById(event.getOrderId())) {
            logger.info(
                    "Ignoring already processed creation event "
                            + "for order {}",
                    event.getOrderId()
            );

            return false;
        }

        String transactionHash =
                blockchainWriteService.createOrderOnBlockchain(
                        event.getOrderId(),
                        event.getDataHash()
                );

        saveOrderView(
                event.getOrderId(),
                OrderStatus.CREATED,
                event.getCategory(),
                event.getOrderValue(),
                event.getItemCount(),
                event.getTimestamp(),
                event.getSequenceNumber()
        );

        logger.info(
                "Order {} created on blockchain with transaction {}",
                event.getOrderId(),
                transactionHash
        );

        processNextPendingEvent(
                event.getOrderId(),
                event.getSequenceNumber() + 1
        );

        return true;
    }

    private boolean handleOrderApproved(
            RoutedEventMessage routedMessage,
            String queue,
            String originalMessage
    ) throws JsonProcessingException {

        OrderApprovedEvent event =
                objectMapper.readValue(
                        routedMessage.getPayload(),
                        OrderApprovedEvent.class
                );

        OrderView view = validateLifecycleEvent(
                routedMessage,
                queue,
                originalMessage,
                event.getOrderId(),
                event.getSequenceNumber()
        );

        if (view == null) {
            return false;
        }

        String transactionHash =
                blockchainWriteService.approveOrderOnBlockchain(
                        event.getOrderId()
                );

        updateStatus(
                view,
                OrderStatus.APPROVED,
                event.getTimestamp(),
                event.getSequenceNumber()
        );

        logger.info(
                "Order {} approved on blockchain with transaction {}",
                event.getOrderId(),
                transactionHash
        );

        processNextPendingEvent(
                event.getOrderId(),
                event.getSequenceNumber() + 1
        );

        return true;
    }

    private boolean handleOrderDispatched(
            RoutedEventMessage routedMessage,
            String queue,
            String originalMessage
    ) throws JsonProcessingException {

        OrderDispatchedEvent event =
                objectMapper.readValue(
                        routedMessage.getPayload(),
                        OrderDispatchedEvent.class
                );

        OrderView view = validateLifecycleEvent(
                routedMessage,
                queue,
                originalMessage,
                event.getOrderId(),
                event.getSequenceNumber()
        );

        if (view == null) {
            return false;
        }

        String transactionHash =
                blockchainWriteService.dispatchOrderOnBlockchain(
                        event.getOrderId()
                );

        updateStatus(
                view,
                OrderStatus.DISPATCHED,
                event.getTimestamp(),
                event.getSequenceNumber()
        );

        logger.info(
                "Order {} dispatched on blockchain with transaction {}",
                event.getOrderId(),
                transactionHash
        );

        processNextPendingEvent(
                event.getOrderId(),
                event.getSequenceNumber() + 1
        );

        return true;
    }

    private boolean handleOrderCompleted(
            RoutedEventMessage routedMessage,
            String queue,
            String originalMessage
    ) throws JsonProcessingException {

        OrderCompletedEvent event =
                objectMapper.readValue(
                        routedMessage.getPayload(),
                        OrderCompletedEvent.class
                );

        OrderView view = validateLifecycleEvent(
                routedMessage,
                queue,
                originalMessage,
                event.getOrderId(),
                event.getSequenceNumber()
        );

        if (view == null) {
            return false;
        }

        String transactionHash =
                blockchainWriteService.completeOrderOnBlockchain(
                        event.getOrderId()
                );

        updateStatus(
                view,
                OrderStatus.COMPLETED,
                event.getTimestamp(),
                event.getSequenceNumber()
        );

        logger.info(
                "Order {} completed on blockchain with transaction {}",
                event.getOrderId(),
                transactionHash
        );

        return true;
    }

    private boolean handleOrderCancelled(
            RoutedEventMessage routedMessage,
            String queue,
            String originalMessage
    ) throws JsonProcessingException {

        OrderCancelledEvent event =
                objectMapper.readValue(
                        routedMessage.getPayload(),
                        OrderCancelledEvent.class
                );

        OrderView view = validateLifecycleEvent(
                routedMessage,
                queue,
                originalMessage,
                event.getOrderId(),
                event.getSequenceNumber()
        );

        if (view == null) {
            return false;
        }

        String transactionHash =
                blockchainWriteService.cancelOrderOnBlockchain(
                        event.getOrderId(),
                        event.getReason()
                );

        updateStatus(
                view,
                OrderStatus.CANCELLED,
                event.getTimestamp(),
                event.getSequenceNumber()
        );

        logger.info(
                "Order {} cancelled on blockchain with transaction {}",
                event.getOrderId(),
                transactionHash
        );

        return true;
    }

    // ------------------- Confirmed order view updates -------------------

    private void saveOrderView(
            String orderId,
            OrderStatus status,
            String category,
            double orderValue,
            int itemCount,
            long eventTimestamp,
            int lastProcessedSequenceNumber
    ) {
        OrderView view = new OrderView(
                orderId,
                status.name(),
                category,
                orderValue,
                itemCount,
                eventTimestamp,
                lastProcessedSequenceNumber
        );

        orderViewRepository.save(view);

        logger.info(
                "OrderView stored for order {} with status {} "
                        + "at sequence {}",
                orderId,
                status,
                lastProcessedSequenceNumber
        );
    }

    private void updateStatus(
            OrderView view,
            OrderStatus newStatus,
            long eventTimestamp,
            int sequenceNumber
    ) {
        view.setStatus(newStatus.name());
        view.setLastEventTimestamp(eventTimestamp);
        view.setLastProcessedSequenceNumber(sequenceNumber);

        orderViewRepository.save(view);

        logger.info(
                "OrderView updated for order {} to status {} "
                        + "at sequence {}",
                view.getOrderId(),
                newStatus,
                sequenceNumber
        );
    }

    // ------------------- Sequence validation -------------------

    private SequenceCheck checkSequence(
            String orderId,
            int receivedSequenceNumber
    ) {
        OrderView view = orderViewRepository.findById(orderId)
                .orElse(null);

        /*
         * If the creation event has not completed yet, every later
         * lifecycle event is considered a future event.
         */
        if (view == null) {
            return new SequenceCheck(
                    SequenceState.FUTURE,
                    null
            );
        }

        int expectedSequenceNumber =
                view.getLastProcessedSequenceNumber() + 1;

        if (receivedSequenceNumber < expectedSequenceNumber) {
            return new SequenceCheck(
                    SequenceState.ALREADY_PROCESSED,
                    view
            );
        }

        if (receivedSequenceNumber > expectedSequenceNumber) {
            return new SequenceCheck(
                    SequenceState.FUTURE,
                    view
            );
        }

        return new SequenceCheck(
                SequenceState.EXPECTED,
                view
        );
    }

    private void logDuplicate(
            String orderId,
            int sequenceNumber
    ) {
        logger.info(
                "Ignoring already processed event for order {} "
                        + "at sequence {}",
                orderId,
                sequenceNumber
        );
    }

    // ------------------- Pending-event resequencing -------------------

    private void savePendingEvent(
            RoutedEventMessage routedMessage,
            String orderId,
            int sequenceNumber,
            String queue,
            String originalMessage
    ) {
        if (pendingOrdersRepository.existsById(
                routedMessage.getRoutingDecisionId()
        )) {
            logger.debug(
                    "Pending event {} is already buffered",
                    routedMessage.getRoutingDecisionId()
            );

            return;
        }

        PendingOrdersView pendingEvent =
                new PendingOrdersView(
                        routedMessage.getRoutingDecisionId(),
                        orderId,
                        sequenceNumber,
                        queue,
                        originalMessage,
                        System.currentTimeMillis()
                );

        pendingOrdersRepository.save(pendingEvent);

        logger.info(
                "Buffered future event for order {} at sequence {}",
                orderId,
                sequenceNumber
        );
    }

    private void processNextPendingEvent(
            String orderId,
            int nextSequenceNumber
    ) {
        pendingOrdersRepository
                .findByOrderIdAndSequenceNumber(
                        orderId,
                        nextSequenceNumber
                )
                .ifPresent(this::processPendingEvent);
    }

    private void processPendingEvent(
            PendingOrdersView pendingEvent
    ) {
        try {
            boolean processed = processMessage(
                    pendingEvent.getMessagePayload(),
                    pendingEvent.getQueueName()
            );

            if (processed) {
                pendingOrdersRepository.delete(pendingEvent);

                logger.info(
                        "Processed and removed buffered event {} "
                                + "for order {} at sequence {}",
                        pendingEvent.getRoutingDecisionId(),
                        pendingEvent.getOrderId(),
                        pendingEvent.getSequenceNumber()
                );
            }

        } catch (Exception exception) {
            /*
             * Keep the row so it can be retried later. Do not propagate
             * this exception and cause the already completed predecessor
             * event to be redelivered.
             */
            logger.error(
                    "Failed to process buffered event {} for order {} "
                            + "at sequence {}",
                    pendingEvent.getRoutingDecisionId(),
                    pendingEvent.getOrderId(),
                    pendingEvent.getSequenceNumber(),
                    exception
            );
        }
    }

    private enum SequenceState {
        EXPECTED,
        FUTURE,
        ALREADY_PROCESSED
    }

    private record SequenceCheck(
            SequenceState state,
            OrderView view
    ) {
    }

    private OrderView validateLifecycleEvent(
            RoutedEventMessage routedMessage,
            String queue,
            String originalMessage,
            String orderId,
            int sequenceNumber
    ) {
        SequenceCheck sequenceCheck =
                checkSequence(orderId, sequenceNumber);

        if (sequenceCheck.state()
                == SequenceState.ALREADY_PROCESSED) {

            logDuplicate(orderId, sequenceNumber);
            return null;
        }

        if (sequenceCheck.state()
                == SequenceState.FUTURE) {

            savePendingEvent(
                    routedMessage,
                    orderId,
                    sequenceNumber,
                    queue,
                    originalMessage
            );

            return null;
        }

        return sequenceCheck.view();
    }
}