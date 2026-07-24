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

    public Order() {
        // Required by Axon
    }

    // --- CREATE ---
    @CommandHandler
    public Order(CreateOrderCommand cmd) {
        AggregateLifecycle.apply(new OrderCreatedEvent(
                cmd.getOrderId(),
                cmd.getCustomerId(),
                cmd.getCategory(),
                cmd.getOrderValue(),
                cmd.getItemCount(),
                cmd.getTimestamp(),
                cmd.getPriority(),
                cmd.getSequenceNumber(),
                cmd.getDataHash()
        ));
    }

    @EventSourcingHandler
    public void on(OrderCreatedEvent evt) {
        this.orderId = evt.getOrderId();
        this.status = OrderStatus.CREATED;
        this.category = evt.getCategory();
        this.orderValue = evt.getOrderValue();
        this.itemCount = evt.getItemCount();
    }

    // --- APPROVE ---
    @CommandHandler
    public void handle(ApproveOrderCommand cmd) {
        if (status != OrderStatus.CREATED) {
            throw new IllegalStateException("Order cannot be approved in state " + status);
        }

        AggregateLifecycle.apply(new OrderApprovedEvent(
                cmd.getOrderId(),
                cmd.getTimestamp(),
                cmd.getSequenceNumber()
        ));
    }

    @EventSourcingHandler
    public void on(OrderApprovedEvent evt) {
        this.status = OrderStatus.APPROVED;
    }

    // --- DISPATCH ---
    @CommandHandler
    public void handle(DispatchOrderCommand cmd) {
        if (status != OrderStatus.APPROVED) {
            throw new IllegalStateException("Order cannot be dispatched in state " + status);
        }

        AggregateLifecycle.apply(new OrderDispatchedEvent(
                cmd.getOrderId(),
                cmd.getTimestamp(),
                cmd.getSequenceNumber()
        ));
    }

    @EventSourcingHandler
    public void on(OrderDispatchedEvent evt) {
        this.status = OrderStatus.DISPATCHED;
    }

    // --- COMPLETE ---
    @CommandHandler
    public void handle(CompleteOrderCommand cmd) {
        if (status != OrderStatus.DISPATCHED) {
            throw new IllegalStateException("Order cannot be completed in state " + status);
        }

        AggregateLifecycle.apply(new OrderCompletedEvent(
                cmd.getOrderId(),
                cmd.getTimestamp(),
                cmd.getSequenceNumber()
        ));
    }

    @EventSourcingHandler
    public void on(OrderCompletedEvent evt) {
        this.status = OrderStatus.COMPLETED;
    }

    // --- CANCEL ---
    @CommandHandler
    public void handle(CancelOrderCommand cmd) {
        if (status == OrderStatus.COMPLETED) {
            throw new IllegalStateException("Completed orders cannot be cancelled");
        }

        AggregateLifecycle.apply(new OrderCancelledEvent(
                cmd.getOrderId(),
                cmd.getTimestamp(),
                cmd.getSequenceNumber(),
                cmd.getReason()
        ));
    }

    @EventSourcingHandler
    public void on(OrderCancelledEvent evt) {
        this.status = OrderStatus.CANCELLED;
    }
}