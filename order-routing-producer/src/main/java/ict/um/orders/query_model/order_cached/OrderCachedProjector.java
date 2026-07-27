package ict.um.orders.query_model.order_cached;

import ict.um.orders.core_api.enums.OrderStatus;
import ict.um.orders.core_api.events.*;
import ict.um.orders.core_api.messaging.RoutedEventType;
import ict.um.orders.core_api.queries.GetCacheByOrderIdQuery;
import ict.um.orders.routing.OrderRoutingContext;
import ict.um.orders.services.messaging.RabbitEventPublisher;
import ict.um.orders.services.routing.RoutingService;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OrderCachedProjector {

    private final OrderCachedViewRepository repository;
    private final RabbitEventPublisher publisher;
    private final RoutingService routingService;

    @Autowired
    public OrderCachedProjector(OrderCachedViewRepository repository,
                                RabbitEventPublisher publisher,
                                RoutingService routingService) {
        this.repository = repository;
        this.publisher = publisher;
        this.routingService = routingService;
    }

    // --- EVENT HANDLERS ---

    @EventHandler
    public void on(OrderCreatedEvent event) {
        OrderCachedView view = new OrderCachedView(
                event.getOrderId(),
                OrderStatus.CREATED.name(),
                event.getCategory(),
                event.getOrderValue(),
                event.getItemCount(),
                event.getTimestamp()
        );
        repository.save(view);

        routeAndPublish(
                event,
                RoutedEventType.ORDER_CREATED,
                view,
                OrderStatus.CREATED,
                event.getTimestamp()
        );
    }

    @EventHandler
    public void on(OrderApprovedEvent event) {
        OrderCachedView view = repository.findById(event.getOrderId()).orElseThrow();
        view.setStatus(OrderStatus.APPROVED.name());
        view.setLastEventTimestamp(event.getTimestamp());
        repository.save(view);

        routeAndPublish(
                event,
                RoutedEventType.ORDER_APPROVED,
                view,
                OrderStatus.APPROVED,
                event.getTimestamp()
        );
    }

    @EventHandler
    public void on(OrderDispatchedEvent event) {
        OrderCachedView view = repository.findById(event.getOrderId()).orElseThrow();
        view.setStatus(OrderStatus.DISPATCHED.name());
        view.setLastEventTimestamp(event.getTimestamp());
        repository.save(view);

        routeAndPublish(
                event,
                RoutedEventType.ORDER_DISPATCHED,
                view,
                OrderStatus.DISPATCHED,
                event.getTimestamp()
        );
    }

    @EventHandler
    public void on(OrderCompletedEvent event) {
        OrderCachedView view = repository.findById(event.getOrderId()).orElseThrow();
        view.setStatus(OrderStatus.COMPLETED.name());
        view.setLastEventTimestamp(event.getTimestamp());
        repository.save(view);

        routeAndPublish(
                event,
                RoutedEventType.ORDER_COMPLETED,
                view,
                OrderStatus.COMPLETED,
                event.getTimestamp()
        );
    }

    @EventHandler
    public void on(OrderCancelledEvent event) {
        OrderCachedView view = repository.findById(event.getOrderId()).orElseThrow();
        view.setStatus(OrderStatus.CANCELLED.name());
        view.setLastEventTimestamp(event.getTimestamp());
        repository.save(view);

        routeAndPublish(
                event,
                RoutedEventType.ORDER_CANCELLED,
                view,
                OrderStatus.CANCELLED,
                event.getTimestamp()
        );
    }

    // --- QUERY HANDLER ---
    @QueryHandler
    public OrderCachedView handle(GetCacheByOrderIdQuery query) {
        return repository.findById(query.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Cached order not found: " + query.getOrderId()
                ));
    }

    private void routeAndPublish(
            Object event,
            RoutedEventType eventType,
            OrderCachedView view,
            OrderStatus status,
            long timestamp
    ) {
        OrderRoutingContext context = new OrderRoutingContext(
                view.getOrderId(),
                status,
                view.getCategory(),
                view.getOrderValue(),
                view.getItemCount(),
                timestamp
        );

        String queue = routingService.route(context);

        publisher.publish(queue, eventType, event);
    }
}
