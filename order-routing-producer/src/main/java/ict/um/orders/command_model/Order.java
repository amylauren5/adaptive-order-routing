package ict.um.orders.command_model;

import ict.um.orders.core_api.commands.*;
import ict.um.orders.core_api.events.*;
import ict.um.orders.core_api.enums.OrderStatus;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;

@Aggregate
public class Order {

    @AggregateIdentifier
    private String orderId;

    private OrderStatus status;
    private String category;
    private double orderValue;
    private int itemCount;

    protected Order() {
        // Required by Axon
    }

    @CommandHandler
    public Order(CreateOrderCommand command) {
        validateCreation(command);

        AggregateLifecycle.apply(new OrderCreatedEvent(
                command.getOrderId(),
                command.getCustomerId(),
                command.getCategory(),
                command.getOrderValue(),
                command.getItemCount(),
                command.getTimestamp(),
                command.getPriority(),
                command.getSequenceNumber(),
                command.getDataHash()
        ));
    }

    @CommandHandler
    public void handle(ApproveOrderCommand command) {
        requireStatus(OrderStatus.CREATED, "approved");

        AggregateLifecycle.apply(new OrderApprovedEvent(
                command.getOrderId(),
                command.getTimestamp(),
                command.getSequenceNumber()
        ));
    }

    @CommandHandler
    public void handle(DispatchOrderCommand command) {
        requireStatus(OrderStatus.APPROVED, "dispatched");

        AggregateLifecycle.apply(new OrderDispatchedEvent(
                command.getOrderId(),
                command.getTimestamp(),
                command.getSequenceNumber()
        ));
    }

    @CommandHandler
    public void handle(CompleteOrderCommand command) {
        requireStatus(OrderStatus.DISPATCHED, "completed");

        AggregateLifecycle.apply(new OrderCompletedEvent(
                command.getOrderId(),
                command.getTimestamp(),
                command.getSequenceNumber()
        ));
    }

    @CommandHandler
    public void handle(CancelOrderCommand command) {
        if (status == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Order is already cancelled");
        }

        if (status == OrderStatus.COMPLETED) {
            throw new IllegalStateException(
                    "Completed orders cannot be cancelled"
            );
        }

        AggregateLifecycle.apply(new OrderCancelledEvent(
                command.getOrderId(),
                command.getTimestamp(),
                command.getSequenceNumber(),
                command.getReason()
        ));
    }

    @EventSourcingHandler
    public void on(OrderCreatedEvent event) {
        orderId = event.getOrderId();
        status = OrderStatus.CREATED;
        category = event.getCategory();
        orderValue = event.getOrderValue();
        itemCount = event.getItemCount();
    }

    @EventSourcingHandler
    public void on(OrderApprovedEvent event) {
        status = OrderStatus.APPROVED;
    }

    @EventSourcingHandler
    public void on(OrderDispatchedEvent event) {
        status = OrderStatus.DISPATCHED;
    }

    @EventSourcingHandler
    public void on(OrderCompletedEvent event) {
        status = OrderStatus.COMPLETED;
    }

    @EventSourcingHandler
    public void on(OrderCancelledEvent event) {
        status = OrderStatus.CANCELLED;
    }

    private void requireStatus(
            OrderStatus requiredStatus,
            String operation
    ) {
        if (status != requiredStatus) {
            throw new IllegalStateException(
                    "Order cannot be " + operation + " in state " + status
            );
        }
    }

    private static void validateCreation(CreateOrderCommand command) {
        requireText(command.getOrderId(), "Order ID");
        requireText(command.getCustomerId(), "Customer ID");
        requireText(command.getCategory(), "Category");
        requireText(command.getDataHash(), "Data hash");

        if (!Double.isFinite(command.getOrderValue())
                || command.getOrderValue() < 0) {
            throw new IllegalArgumentException(
                    "Order value must be finite and non-negative"
            );
        }

        if (command.getItemCount() <= 0) {
            throw new IllegalArgumentException(
                    "Item count must be greater than zero"
            );
        }

        if (command.getPriority() < 1 || command.getPriority() > 3) {
            throw new IllegalArgumentException(
                    "Priority must be between 1 and 3"
            );
        }

        if (command.getTimestamp() <= 0) {
            throw new IllegalArgumentException(
                    "Timestamp must be positive"
            );
        }

        if (command.getSequenceNumber() < 0) {
            throw new IllegalArgumentException(
                    "Sequence number cannot be negative"
            );
        }
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}