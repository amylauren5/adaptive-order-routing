package ict.um.orders.query_model.order_submitted;

import ict.um.orders.coreapi.events.*;
import ict.um.orders.coreapi.enums.OrderStatus;
import ict.um.orders.coreapi.queries.GetSubmittedByOrderIdQuery;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OrderSubmittedProjector {

    private final OrderSubmittedViewRepository repository;

    @Autowired
    public OrderSubmittedProjector(OrderSubmittedViewRepository repository) {
        this.repository = repository;
    }

    @EventHandler
    public void on(OrderCreatedEvent evt) {
        OrderSubmittedView view = new OrderSubmittedView(
                evt.getOrderId(),
                evt.getCustomerId(),
                evt.getCategory(),
                evt.getOrderValue(),
                evt.getItemCount(),
                OrderStatus.CREATED.name(),
                evt.getTimestamp(),
                evt.getSequenceNumber()
        );
        repository.save(view);
    }

    @EventHandler
    public void on(OrderApprovedEvent evt) {
        OrderSubmittedView view = repository.findById(evt.getOrderId()).orElseThrow();
        view.setStatus(OrderStatus.APPROVED.name());
        view.setLastEventTimestamp(evt.getTimestamp());
        view.setLastSequenceNumber(evt.getSequenceNumber());
        repository.save(view);
    }

    @EventHandler
    public void on(OrderDispatchedEvent evt) {
        OrderSubmittedView view = repository.findById(evt.getOrderId()).orElseThrow();
        view.setStatus(OrderStatus.DISPATCHED.name());
        view.setLastEventTimestamp(evt.getTimestamp());
        view.setLastSequenceNumber(evt.getSequenceNumber());
        repository.save(view);
    }

    @EventHandler
    public void on(OrderCompletedEvent evt) {
        OrderSubmittedView view = repository.findById(evt.getOrderId()).orElseThrow();
        view.setStatus(OrderStatus.COMPLETED.name());
        view.setLastEventTimestamp(evt.getTimestamp());
        view.setLastSequenceNumber(evt.getSequenceNumber());
        repository.save(view);
    }

    @EventHandler
    public void on(OrderCancelledEvent evt) {
        OrderSubmittedView view = repository.findById(evt.getOrderId()).orElseThrow();
        view.setStatus(OrderStatus.CANCELLED.name());
        view.setLastEventTimestamp(evt.getTimestamp());
        view.setLastSequenceNumber(evt.getSequenceNumber());
        repository.save(view);
    }

    @QueryHandler
    public OrderSubmittedView handle(GetSubmittedByOrderIdQuery query) {
        return repository.findById(query.getOrderId()).orElseThrow();
    }
}