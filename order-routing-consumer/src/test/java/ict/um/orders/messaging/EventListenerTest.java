package ict.um.orders.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import ict.um.orders.core_api.events.OrderApprovedEvent;
import ict.um.orders.core_api.events.OrderCreatedEvent;
import ict.um.orders.core_api.events.OrderDispatchedEvent;
import ict.um.orders.core_api.messaging.RoutedEventMessage;
import ict.um.orders.core_api.messaging.RoutedEventType;
import ict.um.orders.query_model.orders.OrderView;
import ict.um.orders.query_model.orders.OrderViewRepository;
import ict.um.orders.query_model.pending_orders.PendingOrdersRepository;
import ict.um.orders.query_model.pending_orders.PendingOrdersView;
import ict.um.orders.services.BlockchainWriteService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static ict.um.orders.core_api.config.QueueNames.QUEUE_1;
import static ict.um.orders.core_api.config.QueueNames.QUEUE_2;
import static ict.um.orders.core_api.config.QueueNames.QUEUE_3;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventListenerTest {

    private static final String ORDER_ID = "order-1";

    private ObjectMapper objectMapper;

    private BlockchainWriteService blockchainWriteService;
    private OrderViewRepository orderViewRepository;
    private PendingOrdersRepository pendingOrdersRepository;

    private EventListener listener;

    /*
     * In-memory state used behind the mocked repositories.
     *
     * Concurrent maps are used because one of the tests deliberately
     * invokes different RabbitMQ listener methods concurrently.
     */
    private Map<String, OrderView> orderViews;
    private Map<String, PendingOrdersView> pendingEvents;

    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        blockchainWriteService =
                Mockito.mock(BlockchainWriteService.class);

        orderViewRepository =
                Mockito.mock(OrderViewRepository.class);

        pendingOrdersRepository =
                Mockito.mock(PendingOrdersRepository.class);

        orderViews =
                new ConcurrentHashMap<>();

        pendingEvents =
                new ConcurrentHashMap<>();

        configureOrderViewRepository();
        configurePendingOrdersRepository();
        configureBlockchainService();

        listener = new EventListener(
                objectMapper,
                blockchainWriteService,
                orderViewRepository,
                pendingOrdersRepository,
                Optional.empty(),
                Optional.empty()
        );

        executorService =
                Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    // --------------------------------------------------
    // Normal in-order processing
    // --------------------------------------------------

    @Test
    void shouldProcessLifecycleEventsInOrder()
            throws Exception {

        listener.receiveProcessingQueue1(
                createdMessage(
                        "decision-0"
                )
        );

        listener.receiveProcessingQueue2(
                approvedMessage(
                        "decision-1"
                )
        );

        listener.receiveProcessingQueue3(
                dispatchedMessage(
                        "decision-2"
                )
        );

        OrderView view =
                orderViews.get(ORDER_ID);

        assertEquals(
                2,
                view.getLastProcessedSequenceNumber()
        );

        assertEquals(
                "DISPATCHED",
                view.getStatus()
        );

        assertTrue(
                pendingEvents.isEmpty()
        );

        verify(
                blockchainWriteService,
                times(1)
        ).createOrderOnBlockchain(
                ORDER_ID,
                "hash"
        );

        verify(
                blockchainWriteService,
                times(1)
        ).approveOrderOnBlockchain(
                ORDER_ID
        );

        verify(
                blockchainWriteService,
                times(1)
        ).dispatchOrderOnBlockchain(
                ORDER_ID
        );
    }

    // --------------------------------------------------
    // Future event must remain buffered until predecessor
    // --------------------------------------------------

    @Test
    void shouldBufferFutureEventAndReleaseItAfterPredecessor()
            throws Exception {

        listener.receiveProcessingQueue1(
                createdMessage(
                        "decision-0"
                )
        );

        /*
         * Sequence 2 arrives while sequence 1 is still expected.
         */
        listener.receiveProcessingQueue3(
                dispatchedMessage(
                        "decision-2"
                )
        );

        OrderView afterFutureEvent =
                orderViews.get(ORDER_ID);

        assertEquals(
                0,
                afterFutureEvent
                        .getLastProcessedSequenceNumber()
        );

        assertEquals(
                1,
                pendingEvents.size()
        );

        assertTrue(
                pendingEvents.values()
                        .stream()
                        .anyMatch(
                                event ->
                                        event.getSequenceNumber()
                                                == 2
                        )
        );

        verify(
                blockchainWriteService,
                times(0)
        ).dispatchOrderOnBlockchain(
                ORDER_ID
        );

        /*
         * Sequence 1 now arrives. It should be processed and the
         * buffered sequence-2 event should immediately follow.
         */
        listener.receiveProcessingQueue2(
                approvedMessage(
                        "decision-1"
                )
        );

        OrderView finalView =
                orderViews.get(ORDER_ID);

        assertEquals(
                2,
                finalView.getLastProcessedSequenceNumber()
        );

        assertEquals(
                "DISPATCHED",
                finalView.getStatus()
        );

        assertTrue(
                pendingEvents.isEmpty()
        );

        verify(
                blockchainWriteService,
                times(1)
        ).approveOrderOnBlockchain(
                ORDER_ID
        );

        verify(
                blockchainWriteService,
                times(1)
        ).dispatchOrderOnBlockchain(
                ORDER_ID
        );
    }

    // --------------------------------------------------
    // Several events can arrive before creation
    // --------------------------------------------------

    @Test
    void shouldReleaseMultipleBufferedEventsInSequence()
            throws Exception {

        /*
         * Neither sequence 1 nor sequence 2 can be processed because
         * the creation event has not yet established OrderView.
         */
        listener.receiveProcessingQueue3(
                dispatchedMessage(
                        "decision-2"
                )
        );

        listener.receiveProcessingQueue2(
                approvedMessage(
                        "decision-1"
                )
        );

        assertFalse(
                pendingEvents.isEmpty()
        );

        assertEquals(
                2,
                pendingEvents.size()
        );

        /*
         * Creation establishes sequence 0. The listener should then
         * release sequence 1, which in turn releases sequence 2.
         */
        listener.receiveProcessingQueue1(
                createdMessage(
                        "decision-0"
                )
        );

        OrderView finalView =
                orderViews.get(ORDER_ID);

        assertEquals(
                2,
                finalView.getLastProcessedSequenceNumber()
        );

        assertEquals(
                "DISPATCHED",
                finalView.getStatus()
        );

        assertTrue(
                pendingEvents.isEmpty()
        );

        verify(
                blockchainWriteService,
                times(1)
        ).createOrderOnBlockchain(
                ORDER_ID,
                "hash"
        );

        verify(
                blockchainWriteService,
                times(1)
        ).approveOrderOnBlockchain(
                ORDER_ID
        );

        verify(
                blockchainWriteService,
                times(1)
        ).dispatchOrderOnBlockchain(
                ORDER_ID
        );
    }

    // --------------------------------------------------
    // Redelivery must not repeat a side effect
    // --------------------------------------------------

    @Test
    void shouldIgnoreDuplicateAlreadyProcessedEvent()
            throws Exception {

        listener.receiveProcessingQueue1(
                createdMessage(
                        "decision-0"
                )
        );

        listener.receiveProcessingQueue2(
                approvedMessage(
                        "decision-1"
                )
        );

        /*
         * Redelivery of sequence 1 with a different routing-decision
         * identifier must still be recognised from order sequence
         * state as already processed.
         */
        listener.receiveProcessingQueue2(
                approvedMessage(
                        "decision-1-redelivery"
                )
        );

        OrderView view =
                orderViews.get(ORDER_ID);

        assertEquals(
                1,
                view.getLastProcessedSequenceNumber()
        );

        assertEquals(
                "APPROVED",
                view.getStatus()
        );

        verify(
                blockchainWriteService,
                times(1)
        ).approveOrderOnBlockchain(
                ORDER_ID
        );
    }

    // --------------------------------------------------
    // Concurrent queue listeners must preserve order
    // --------------------------------------------------

    @Test
    void shouldPreserveSequenceUnderConcurrentDelivery()
            throws Exception {

        /*
         * Establish sequence 0 first.
         */
        listener.receiveProcessingQueue1(
                createdMessage(
                        "decision-0"
                )
        );

        String approvalMessage =
                approvedMessage(
                        "decision-1"
                );

        String dispatchMessage =
                dispatchedMessage(
                        "decision-2"
                );

        CountDownLatch ready =
                new CountDownLatch(2);

        CountDownLatch start =
                new CountDownLatch(1);

        Future<?> approvalFuture =
                executorService.submit(() -> {
                    ready.countDown();
                    await(start);

                    listener.receiveProcessingQueue2(
                            approvalMessage
                    );
                });

        Future<?> dispatchFuture =
                executorService.submit(() -> {
                    ready.countDown();
                    await(start);

                    listener.receiveProcessingQueue3(
                            dispatchMessage
                    );
                });

        assertTrue(
                ready.await(
                        5,
                        TimeUnit.SECONDS
                )
        );

        /*
         * Release both listener calls at approximately the same time.
         */
        start.countDown();

        approvalFuture.get(
                5,
                TimeUnit.SECONDS
        );

        dispatchFuture.get(
                5,
                TimeUnit.SECONDS
        );

        OrderView finalView =
                orderViews.get(ORDER_ID);

        assertEquals(
                2,
                finalView.getLastProcessedSequenceNumber()
        );

        assertEquals(
                "DISPATCHED",
                finalView.getStatus()
        );

        assertTrue(
                pendingEvents.isEmpty()
        );

        /*
         * Most importantly, neither blockchain side effect may be
         * executed twice despite the concurrent listener calls.
         */
        verify(
                blockchainWriteService,
                times(1)
        ).approveOrderOnBlockchain(
                ORDER_ID
        );

        verify(
                blockchainWriteService,
                times(1)
        ).dispatchOrderOnBlockchain(
                ORDER_ID
        );
    }

    // ==================================================
    // Mock repository behaviour
    // ==================================================

    private void configureOrderViewRepository() {
        when(
                orderViewRepository.findById(
                        anyString()
                )
        ).thenAnswer(invocation -> {
            String orderId =
                    invocation.getArgument(0);

            return Optional.ofNullable(
                    orderViews.get(orderId)
            );
        });

        when(
                orderViewRepository.existsById(
                        anyString()
                )
        ).thenAnswer(invocation -> {
            String orderId =
                    invocation.getArgument(0);

            return orderViews.containsKey(
                    orderId
            );
        });

        when(
                orderViewRepository.save(
                        any(OrderView.class)
                )
        ).thenAnswer(invocation -> {
            OrderView view =
                    invocation.getArgument(0);

            orderViews.put(
                    view.getOrderId(),
                    view
            );

            return view;
        });
    }

    private void configurePendingOrdersRepository() {
        when(
                pendingOrdersRepository.existsById(
                        anyString()
                )
        ).thenAnswer(invocation -> {
            String routingDecisionId =
                    invocation.getArgument(0);

            return pendingEvents.containsKey(
                    routingDecisionId
            );
        });

        when(
                pendingOrdersRepository.saveAndFlush(
                        any(PendingOrdersView.class)
                )
        ).thenAnswer(invocation -> {
            PendingOrdersView event =
                    invocation.getArgument(0);

            pendingEvents.put(
                    event.getRoutingDecisionId(),
                    event
            );

            return event;
        });

        when(
                pendingOrdersRepository
                        .findByOrderIdAndSequenceNumber(
                                anyString(),
                                anyInt()
                        )
        ).thenAnswer(invocation -> {
            String orderId =
                    invocation.getArgument(0);

            int sequenceNumber =
                    invocation.getArgument(1);

            return pendingEvents.values()
                    .stream()
                    .filter(
                            event ->
                                    event.getOrderId()
                                            .equals(orderId)
                    )
                    .filter(
                            event ->
                                    event.getSequenceNumber()
                                            == sequenceNumber
                    )
                    .findFirst();
        });

        doAnswer(invocation -> {
            PendingOrdersView event =
                    invocation.getArgument(0);

            pendingEvents.remove(
                    event.getRoutingDecisionId()
            );

            return null;
        }).when(
                pendingOrdersRepository
        ).delete(
                any(PendingOrdersView.class)
        );
    }

    private void configureBlockchainService() {
        when(
                blockchainWriteService
                        .createOrderOnBlockchain(
                                anyString(),
                                anyString()
                        )
        ).thenReturn(
                "tx-create"
        );

        when(
                blockchainWriteService
                        .approveOrderOnBlockchain(
                                anyString()
                        )
        ).thenReturn(
                "tx-approve"
        );

        when(
                blockchainWriteService
                        .dispatchOrderOnBlockchain(
                                anyString()
                        )
        ).thenReturn(
                "tx-dispatch"
        );
    }

    // ==================================================
    // Message builders
    // ==================================================

    private String createdMessage(
            String routingDecisionId
    ) throws Exception {

        OrderCreatedEvent event =
                new OrderCreatedEvent(
                        ORDER_ID,
                        "customer-1",
                        "books",
                        100.0,
                        1,
                        System.currentTimeMillis(),
                        0,
                        "hash"
                );

        return routedMessage(
                routingDecisionId,
                QUEUE_1,
                RoutedEventType.ORDER_CREATED,
                event
        );
    }

    private String approvedMessage(
            String routingDecisionId
    ) throws Exception {

        OrderApprovedEvent event =
                new OrderApprovedEvent(
                        ORDER_ID,
                        System.currentTimeMillis(),
                        1
                );

        return routedMessage(
                routingDecisionId,
                QUEUE_2,
                RoutedEventType.ORDER_APPROVED,
                event
        );
    }

    private String dispatchedMessage(
            String routingDecisionId
    ) throws Exception {

        OrderDispatchedEvent event =
                new OrderDispatchedEvent(
                        ORDER_ID,
                        System.currentTimeMillis(),
                        2
                );

        return routedMessage(
                routingDecisionId,
                QUEUE_3,
                RoutedEventType.ORDER_DISPATCHED,
                event
        );
    }

    private String routedMessage(
            String routingDecisionId,
            String selectedQueue,
            RoutedEventType eventType,
            Object event
    ) throws Exception {

        String payload =
                objectMapper.writeValueAsString(
                        event
                );

        RoutedEventMessage routedMessage =
                new RoutedEventMessage(
                        routingDecisionId,
                        selectedQueue,
                        System.currentTimeMillis(),
                        eventType,
                        payload
                );

        return objectMapper.writeValueAsString(
                routedMessage
        );
    }

    private static void await(
            CountDownLatch latch
    ) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Interrupted while awaiting concurrent test start",
                    exception
            );
        }
    }
}