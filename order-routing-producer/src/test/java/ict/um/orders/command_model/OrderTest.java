package ict.um.orders.command_model;

import ict.um.orders.core_api.commands.ApproveOrderCommand;
import ict.um.orders.core_api.commands.CancelOrderCommand;
import ict.um.orders.core_api.commands.CompleteOrderCommand;
import ict.um.orders.core_api.commands.CreateOrderCommand;
import ict.um.orders.core_api.commands.DispatchOrderCommand;
import ict.um.orders.core_api.events.OrderApprovedEvent;
import ict.um.orders.core_api.events.OrderCancelledEvent;
import ict.um.orders.core_api.events.OrderCompletedEvent;
import ict.um.orders.core_api.events.OrderCreatedEvent;
import ict.um.orders.core_api.events.OrderDispatchedEvent;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrderTest {

    private static final String ORDER_ID = "order-001";
    private static final String CUSTOMER_ID = "customer-001";
    private static final String CATEGORY = "ELECTRONICS";
    private static final double ORDER_VALUE = 249.99;
    private static final int ITEM_COUNT = 2;
    private static final int PRIORITY = 3;
    private static final String DATA_HASH = "test-data-hash";

    private static final long CREATED_AT = 1_700_000_000_000L;
    private static final long APPROVED_AT = 1_700_000_001_000L;
    private static final long DISPATCHED_AT = 1_700_000_002_000L;
    private static final long COMPLETED_AT = 1_700_000_003_000L;
    private static final long CANCELLED_AT = 1_700_000_004_000L;

    private FixtureConfiguration<Order> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(Order.class);
    }

    // -------------------------------------------------------------------------
    // Creation
    // -------------------------------------------------------------------------

    @Test
    void shouldCreateOrder() {
        CreateOrderCommand command = validCreateCommand();

        fixture.givenNoPriorActivity()
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectEvents(createdEvent());
    }

    @Test
    void shouldRejectBlankOrderId() {
        CreateOrderCommand command = new CreateOrderCommand(
                " ",
                CUSTOMER_ID,
                CATEGORY,
                ORDER_VALUE,
                ITEM_COUNT,
                CREATED_AT,
                PRIORITY,
                1,
                DATA_HASH
        );

        fixture.givenNoPriorActivity()
                .when(command)
                .expectException(IllegalArgumentException.class)
                .expectNoEvents();
    }

    @Test
    void shouldRejectBlankCustomerId() {
        CreateOrderCommand command = new CreateOrderCommand(
                ORDER_ID,
                " ",
                CATEGORY,
                ORDER_VALUE,
                ITEM_COUNT,
                CREATED_AT,
                PRIORITY,
                1,
                DATA_HASH
        );

        fixture.givenNoPriorActivity()
                .when(command)
                .expectException(IllegalArgumentException.class)
                .expectNoEvents();
    }

    @Test
    void shouldRejectBlankCategory() {
        CreateOrderCommand command = new CreateOrderCommand(
                ORDER_ID,
                CUSTOMER_ID,
                " ",
                ORDER_VALUE,
                ITEM_COUNT,
                CREATED_AT,
                PRIORITY,
                1,
                DATA_HASH
        );

        fixture.givenNoPriorActivity()
                .when(command)
                .expectException(IllegalArgumentException.class)
                .expectNoEvents();
    }

    @Test
    void shouldRejectBlankDataHash() {
        CreateOrderCommand command = new CreateOrderCommand(
                ORDER_ID,
                CUSTOMER_ID,
                CATEGORY,
                ORDER_VALUE,
                ITEM_COUNT,
                CREATED_AT,
                PRIORITY,
                1,
                " "
        );

        fixture.givenNoPriorActivity()
                .when(command)
                .expectException(IllegalArgumentException.class)
                .expectNoEvents();
    }

    @Test
    void shouldRejectNegativeOrderValue() {
        CreateOrderCommand command = new CreateOrderCommand(
                ORDER_ID,
                CUSTOMER_ID,
                CATEGORY,
                -1.00,
                ITEM_COUNT,
                CREATED_AT,
                PRIORITY,
                1,
                DATA_HASH
        );

        fixture.givenNoPriorActivity()
                .when(command)
                .expectException(IllegalArgumentException.class)
                .expectNoEvents();
    }

    @Test
    void shouldRejectNaNOrderValue() {
        CreateOrderCommand command = new CreateOrderCommand(
                ORDER_ID,
                CUSTOMER_ID,
                CATEGORY,
                Double.NaN,
                ITEM_COUNT,
                CREATED_AT,
                PRIORITY,
                1,
                DATA_HASH
        );

        fixture.givenNoPriorActivity()
                .when(command)
                .expectException(IllegalArgumentException.class)
                .expectNoEvents();
    }

    @Test
    void shouldRejectInfiniteOrderValue() {
        CreateOrderCommand command = new CreateOrderCommand(
                ORDER_ID,
                CUSTOMER_ID,
                CATEGORY,
                Double.POSITIVE_INFINITY,
                ITEM_COUNT,
                CREATED_AT,
                PRIORITY,
                1,
                DATA_HASH
        );

        fixture.givenNoPriorActivity()
                .when(command)
                .expectException(IllegalArgumentException.class)
                .expectNoEvents();
    }

    @Test
    void shouldRejectZeroItemCount() {
        CreateOrderCommand command = new CreateOrderCommand(
                ORDER_ID,
                CUSTOMER_ID,
                CATEGORY,
                ORDER_VALUE,
                0,
                CREATED_AT,
                PRIORITY,
                1,
                DATA_HASH
        );

        fixture.givenNoPriorActivity()
                .when(command)
                .expectException(IllegalArgumentException.class)
                .expectNoEvents();
    }

    @Test
    void shouldRejectNegativeItemCount() {
        CreateOrderCommand command = new CreateOrderCommand(
                ORDER_ID,
                CUSTOMER_ID,
                CATEGORY,
                ORDER_VALUE,
                -1,
                CREATED_AT,
                PRIORITY,
                1,
                DATA_HASH
        );

        fixture.givenNoPriorActivity()
                .when(command)
                .expectException(IllegalArgumentException.class)
                .expectNoEvents();
    }

    @Test
    void shouldRejectPriorityBelowMinimum() {
        CreateOrderCommand command = new CreateOrderCommand(
                ORDER_ID,
                CUSTOMER_ID,
                CATEGORY,
                ORDER_VALUE,
                ITEM_COUNT,
                CREATED_AT,
                0,
                1,
                DATA_HASH
        );

        fixture.givenNoPriorActivity()
                .when(command)
                .expectException(IllegalArgumentException.class)
                .expectNoEvents();
    }

    @Test
    void shouldRejectPriorityAboveMaximum() {
        CreateOrderCommand command = new CreateOrderCommand(
                ORDER_ID,
                CUSTOMER_ID,
                CATEGORY,
                ORDER_VALUE,
                ITEM_COUNT,
                CREATED_AT,
                4,
                1,
                DATA_HASH
        );

        fixture.givenNoPriorActivity()
                .when(command)
                .expectException(IllegalArgumentException.class)
                .expectNoEvents();
    }

    @Test
    void shouldRejectNonPositiveTimestamp() {
        CreateOrderCommand command = new CreateOrderCommand(
                ORDER_ID,
                CUSTOMER_ID,
                CATEGORY,
                ORDER_VALUE,
                ITEM_COUNT,
                0L,
                PRIORITY,
                1,
                DATA_HASH
        );

        fixture.givenNoPriorActivity()
                .when(command)
                .expectException(IllegalArgumentException.class)
                .expectNoEvents();
    }

    @Test
    void shouldRejectNegativeSequenceNumber() {
        CreateOrderCommand command = new CreateOrderCommand(
                ORDER_ID,
                CUSTOMER_ID,
                CATEGORY,
                ORDER_VALUE,
                ITEM_COUNT,
                CREATED_AT,
                PRIORITY,
                -1,
                DATA_HASH
        );

        fixture.givenNoPriorActivity()
                .when(command)
                .expectException(IllegalArgumentException.class)
                .expectNoEvents();
    }

    // -------------------------------------------------------------------------
    // Approval
    // -------------------------------------------------------------------------

    @Test
    void shouldApproveCreatedOrder() {
        ApproveOrderCommand command = new ApproveOrderCommand(
                ORDER_ID,
                APPROVED_AT,
                2
        );

        fixture.given(createdEvent())
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectEvents(approvedEvent());
    }

    @Test
    void shouldRejectApprovalWhenAlreadyApproved() {
        ApproveOrderCommand command = new ApproveOrderCommand(
                ORDER_ID,
                APPROVED_AT + 1_000,
                3
        );

        fixture.given(
                        createdEvent(),
                        approvedEvent()
                )
                .when(command)
                .expectException(IllegalStateException.class)
                .expectExceptionMessage(
                        "Order cannot be approved in state APPROVED"
                )
                .expectNoEvents();
    }

    @Test
    void shouldRejectApprovalAfterCancellation() {
        ApproveOrderCommand command = new ApproveOrderCommand(
                ORDER_ID,
                APPROVED_AT,
                3
        );

        fixture.given(
                        createdEvent(),
                        cancelledEvent()
                )
                .when(command)
                .expectException(IllegalStateException.class)
                .expectExceptionMessage(
                        "Order cannot be approved in state CANCELLED"
                )
                .expectNoEvents();
    }

    // -------------------------------------------------------------------------
    // Dispatch
    // -------------------------------------------------------------------------

    @Test
    void shouldDispatchApprovedOrder() {
        DispatchOrderCommand command = new DispatchOrderCommand(
                ORDER_ID,
                DISPATCHED_AT,
                3
        );

        fixture.given(
                        createdEvent(),
                        approvedEvent()
                )
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectEvents(dispatchedEvent());
    }

    @Test
    void shouldRejectDispatchBeforeApproval() {
        DispatchOrderCommand command = new DispatchOrderCommand(
                ORDER_ID,
                DISPATCHED_AT,
                2
        );

        fixture.given(createdEvent())
                .when(command)
                .expectException(IllegalStateException.class)
                .expectExceptionMessage(
                        "Order cannot be dispatched in state CREATED"
                )
                .expectNoEvents();
    }

    @Test
    void shouldRejectDispatchAfterCancellation() {
        DispatchOrderCommand command = new DispatchOrderCommand(
                ORDER_ID,
                DISPATCHED_AT,
                3
        );

        fixture.given(
                        createdEvent(),
                        cancelledEvent()
                )
                .when(command)
                .expectException(IllegalStateException.class)
                .expectExceptionMessage(
                        "Order cannot be dispatched in state CANCELLED"
                )
                .expectNoEvents();
    }

    // -------------------------------------------------------------------------
    // Completion
    // -------------------------------------------------------------------------

    @Test
    void shouldCompleteDispatchedOrder() {
        CompleteOrderCommand command = new CompleteOrderCommand(
                ORDER_ID,
                COMPLETED_AT,
                4
        );

        fixture.given(
                        createdEvent(),
                        approvedEvent(),
                        dispatchedEvent()
                )
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectEvents(completedEvent());
    }

    @Test
    void shouldRejectCompletionBeforeDispatch() {
        CompleteOrderCommand command = new CompleteOrderCommand(
                ORDER_ID,
                COMPLETED_AT,
                3
        );

        fixture.given(
                        createdEvent(),
                        approvedEvent()
                )
                .when(command)
                .expectException(IllegalStateException.class)
                .expectExceptionMessage(
                        "Order cannot be completed in state APPROVED"
                )
                .expectNoEvents();
    }

    @Test
    void shouldRejectRepeatedCompletion() {
        CompleteOrderCommand command = new CompleteOrderCommand(
                ORDER_ID,
                COMPLETED_AT + 1_000,
                5
        );

        fixture.given(
                        createdEvent(),
                        approvedEvent(),
                        dispatchedEvent(),
                        completedEvent()
                )
                .when(command)
                .expectException(IllegalStateException.class)
                .expectExceptionMessage(
                        "Order cannot be completed in state COMPLETED"
                )
                .expectNoEvents();
    }

    // -------------------------------------------------------------------------
    // Cancellation
    // -------------------------------------------------------------------------

    @Test
    void shouldCancelCreatedOrder() {
        CancelOrderCommand command = new CancelOrderCommand(
                ORDER_ID,
                CANCELLED_AT,
                2,
                "Customer requested cancellation"
        );

        fixture.given(createdEvent())
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectEvents(cancelledEvent());
    }

    @Test
    void shouldCancelApprovedOrder() {
        CancelOrderCommand command = new CancelOrderCommand(
                ORDER_ID,
                CANCELLED_AT,
                3,
                "Customer requested cancellation"
        );

        fixture.given(
                        createdEvent(),
                        approvedEvent()
                )
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectEvents(new OrderCancelledEvent(
                        ORDER_ID,
                        CANCELLED_AT,
                        3,
                        "Customer requested cancellation"
                ));
    }

    @Test
    void shouldRejectCancellationAfterCompletion() {
        CancelOrderCommand command = new CancelOrderCommand(
                ORDER_ID,
                CANCELLED_AT,
                5,
                "Cancellation requested too late"
        );

        fixture.given(
                        createdEvent(),
                        approvedEvent(),
                        dispatchedEvent(),
                        completedEvent()
                )
                .when(command)
                .expectException(IllegalStateException.class)
                .expectExceptionMessage(
                        "Completed orders cannot be cancelled"
                )
                .expectNoEvents();
    }

    @Test
    void shouldRejectRepeatedCancellation() {
        CancelOrderCommand command = new CancelOrderCommand(
                ORDER_ID,
                CANCELLED_AT + 1_000,
                3,
                "Second cancellation request"
        );

        fixture.given(
                        createdEvent(),
                        cancelledEvent()
                )
                .when(command)
                .expectException(IllegalStateException.class)
                .expectExceptionMessage("Order is already cancelled")
                .expectNoEvents();
    }

    // -------------------------------------------------------------------------
    // Test data
    // -------------------------------------------------------------------------

    private CreateOrderCommand validCreateCommand() {
        return new CreateOrderCommand(
                ORDER_ID,
                CUSTOMER_ID,
                CATEGORY,
                ORDER_VALUE,
                ITEM_COUNT,
                CREATED_AT,
                PRIORITY,
                1,
                DATA_HASH
        );
    }

    private OrderCreatedEvent createdEvent() {
        return new OrderCreatedEvent(
                ORDER_ID,
                CUSTOMER_ID,
                CATEGORY,
                ORDER_VALUE,
                ITEM_COUNT,
                CREATED_AT,
                PRIORITY,
                1,
                DATA_HASH
        );
    }

    private OrderApprovedEvent approvedEvent() {
        return new OrderApprovedEvent(
                ORDER_ID,
                APPROVED_AT,
                2
        );
    }

    private OrderDispatchedEvent dispatchedEvent() {
        return new OrderDispatchedEvent(
                ORDER_ID,
                DISPATCHED_AT,
                3
        );
    }

    private OrderCompletedEvent completedEvent() {
        return new OrderCompletedEvent(
                ORDER_ID,
                COMPLETED_AT,
                4
        );
    }

    private OrderCancelledEvent cancelledEvent() {
        return new OrderCancelledEvent(
                ORDER_ID,
                CANCELLED_AT,
                2,
                "Customer requested cancellation"
        );
    }
}