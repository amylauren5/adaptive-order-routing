package ict.um.orders.query_model.order_cached;

import com.fasterxml.jackson.databind.ObjectMapper;
import ict.um.orders.core_api.events.*;
import ict.um.orders.core_api.enums.OrderStatus;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OrderCachedProjector {

    private final OrderCachedViewRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public OrderCachedProjector(OrderCachedViewRepository repository,
                                RabbitTemplate rabbitTemplate,
                                ObjectMapper objectMapper) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    private void sendToBroker(Object event, String queue) {
        try {
            String json = objectMapper.writeValueAsString(event);
            rabbitTemplate.convertAndSend("", queue, json, new MessagePostProcessor() {
                @Override
                public Message postProcessMessage(Message message) {
                    message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    return message;
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- EVENT HANDLERS ---

    @EventHandler
    public void on(OrderCreatedEvent evt) {
        OrderCachedView view = new OrderCachedView(
                evt.getOrderId(),
                OrderStatus.CREATED.name(),
                evt.getCategory(),
                evt.getOrderValue(),
                evt.getItemCount(),
                evt.getTimestamp()
        );
        repository.save(view);
        sendToBroker(evt, "priority.low");
    }

    @EventHandler
    public void on(OrderApprovedEvent evt) {
        OrderCachedView view = repository.findById(evt.getOrderId()).orElseThrow();
        view.setStatus(OrderStatus.APPROVED.name());
        view.setLastEventTimestamp(evt.getTimestamp());
        repository.save(view);
        sendToBroker(evt, "priority.medium");
    }

    @EventHandler
    public void on(OrderDispatchedEvent evt) {
        OrderCachedView view = repository.findById(evt.getOrderId()).orElseThrow();
        view.setStatus(OrderStatus.DISPATCHED.name());
        view.setLastEventTimestamp(evt.getTimestamp());
        repository.save(view);
        sendToBroker(evt, "priority.medium");
    }

    @EventHandler
    public void on(OrderCompletedEvent evt) {
        OrderCachedView view = repository.findById(evt.getOrderId()).orElseThrow();
        view.setStatus(OrderStatus.COMPLETED.name());
        view.setLastEventTimestamp(evt.getTimestamp());
        repository.save(view);
        sendToBroker(evt, "priority.high");
    }

    @EventHandler
    public void on(OrderCancelledEvent evt) {
        OrderCachedView view = repository.findById(evt.getOrderId()).orElseThrow();
        view.setStatus(OrderStatus.CANCELLED.name());
        view.setLastEventTimestamp(evt.getTimestamp());
        repository.save(view);
        sendToBroker(evt, "priority.high");
    }

}
