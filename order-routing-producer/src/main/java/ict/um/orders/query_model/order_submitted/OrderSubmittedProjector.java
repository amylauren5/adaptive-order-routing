package ict.um.orders.query_model.order_submitted;

import ict.um.orders.command_model.Order;
import ict.um.orders.core_api.enums.OrderStatus;
import ict.um.orders.core_api.events.*;
import ict.um.orders.core_api.queries.GetSubmittedByOrderIdQuery;
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
    public void on(OrderCreatedEvent event) {
        repository.save(new OrderSubmittedView(
                event.getOrderId(),
                event.getCustomerId(),
                event.getCategory(),
                event.getOrderValue(),
                event.getItemCount(),
                event.getTimestamp(),
                event.getPriority(),
                event.getSequenceNumber(),
                OrderStatus.CREATED.name(),
                event.getTimestamp(),
                event.getSequenceNumber()
        ));
    }

    @EventHandler
    public void on(OrderApprovedEvent event) {
        update(
                event.getOrderId(),
                OrderStatus.APPROVED,
                event.getTimestamp(),
                event.getSequenceNumber()
        );
    }

    @EventHandler
    public void on(OrderDispatchedEvent event) {
        update(
                event.getOrderId(),
                OrderStatus.DISPATCHED,
                event.getTimestamp(),
                event.getSequenceNumber()
        );
    }

    @EventHandler
    public void on(OrderCompletedEvent event) {
        update(
                event.getOrderId(),
                OrderStatus.COMPLETED,
                event.getTimestamp(),
                event.getSequenceNumber()
        );
    }

    @EventHandler
    public void on(OrderCancelledEvent event) {
        update(
                event.getOrderId(),
                OrderStatus.CANCELLED,
                event.getTimestamp(),
                event.getSequenceNumber()
        );
    }

    // --- QUERY HANDLER ---
    @QueryHandler
    public OrderSubmittedView handle(GetSubmittedByOrderIdQuery query) {
        return repository.findById(query.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Submitted order not found: " + query.getOrderId()
                ));
    }

    private void update(
            String orderId,
            OrderStatus status,
            long timestamp,
            int sequenceNumber
    ) {
        OrderSubmittedView view = repository.findById(orderId)
                .orElseThrow(() -> new IllegalStateException(
                        "Submitted order not found while processing event: "
                                + orderId
                ));

        view.setStatus(status.name());
        view.setLastEventTimestamp(timestamp);
        view.setLastSequenceNumber(sequenceNumber);

        repository.save(view);
    }
}
