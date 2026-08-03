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
import ict.um.orders.query_model.OrderView;
import ict.um.orders.query_model.OrderViewRepository;
import ict.um.orders.services.BlockchainWriteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.ImmediateRequeueAmqpException;
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
    private final BlockchainWriteService blockchainWriteService;

    public EventListener(
            ObjectMapper objectMapper,
            BlockchainWriteService blockchainWriteService,
            OrderViewRepository orderViewRepository
    ) {
        this.objectMapper = objectMapper;
        this.blockchainWriteService = blockchainWriteService;
        this.orderViewRepository = orderViewRepository;
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

    // ------------------- Envelope dispatch -------------------

    private void receive(String message, String queue) {
        try {
            RoutedEventMessage routedMessage =
                    objectMapper.readValue(
                            message,
                            RoutedEventMessage.class
                    );

            validate(routedMessage);

            logger.info(
                    "Received {} from queue {}",
                    routedMessage.getEventType(),
                    queue
            );

            switch (routedMessage.getEventType()) {
                case ORDER_CREATED ->
                        handleOrderCreated(
                                routedMessage.getPayload()
                        );

                case ORDER_APPROVED ->
                        handleOrderApproved(
                                routedMessage.getPayload()
                        );

                case ORDER_DISPATCHED ->
                        handleOrderDispatched(
                                routedMessage.getPayload()
                        );

                case ORDER_COMPLETED ->
                        handleOrderCompleted(
                                routedMessage.getPayload()
                        );

                case ORDER_CANCELLED ->
                        handleOrderCancelled(
                                routedMessage.getPayload()
                        );
            }

            logger.info(
                    "Successfully processed {} from queue {}",
                    routedMessage.getEventType(),
                    queue
            );

        } catch (OutOfOrderEventException exception) {
            logger.warn(
                    "Requeueing out-of-order message from queue {}: {}",
                    queue,
                    exception.getMessage()
            );

            throw new ImmediateRequeueAmqpException(
                    exception.getMessage(),
                    exception
            );

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

    private void validate(RoutedEventMessage routedMessage) {
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

    private void handleOrderCreated(String payload)
            throws JsonProcessingException {

        OrderCreatedEvent event =
                objectMapper.readValue(
                        payload,
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
         * Prevent duplicate blockchain writes if the message is
         * redelivered after the view has already been persisted.
         */
        if (orderViewRepository.existsById(event.getOrderId())) {
            logger.info(
                    "Order {} has already been processed",
                    event.getOrderId()
            );
            return;
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
    }

    private void handleOrderApproved(String payload)
            throws JsonProcessingException {

        OrderApprovedEvent event =
                objectMapper.readValue(
                        payload,
                        OrderApprovedEvent.class
                );

        OrderView view = requireExpectedSequence(
                event.getOrderId(),
                event.getSequenceNumber()
        );

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
    }

    private void handleOrderDispatched(String payload)
            throws JsonProcessingException {

        OrderDispatchedEvent event =
                objectMapper.readValue(
                        payload,
                        OrderDispatchedEvent.class
                );

        OrderView view = requireExpectedSequence(
                event.getOrderId(),
                event.getSequenceNumber()
        );

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
    }

    private void handleOrderCompleted(String payload)
            throws JsonProcessingException {

        OrderCompletedEvent event =
                objectMapper.readValue(
                        payload,
                        OrderCompletedEvent.class
                );

        OrderView view = requireExpectedSequence(
                event.getOrderId(),
                event.getSequenceNumber()
        );

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
    }

    private void handleOrderCancelled(String payload)
            throws JsonProcessingException {

        OrderCancelledEvent event =
                objectMapper.readValue(
                        payload,
                        OrderCancelledEvent.class
                );

        OrderView view = requireExpectedSequence(
                event.getOrderId(),
                event.getSequenceNumber()
        );

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

    private OrderView requireExpectedSequence(
            String orderId,
            int receivedSequenceNumber
    ) {
        OrderView view = orderViewRepository.findById(orderId)
                .orElseThrow(() -> new OutOfOrderEventException(
                        orderId,
                        0,
                        receivedSequenceNumber
                ));

        int expectedSequenceNumber =
                view.getLastProcessedSequenceNumber() + 1;

        if (receivedSequenceNumber != expectedSequenceNumber) {
            throw new OutOfOrderEventException(
                    orderId,
                    expectedSequenceNumber,
                    receivedSequenceNumber
            );
        }

        return view;
    }
}