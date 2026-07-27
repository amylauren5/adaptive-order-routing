package ict.um.orders.services.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ict.um.orders.core_api.messaging.RoutedEventMessage;
import ict.um.orders.core_api.messaging.RoutedEventType;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class RabbitEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public RabbitEventPublisher(
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    public void publish(
            String queue,
            RoutedEventType eventType,
            Object event
    ) {
        try {
            String eventPayload = objectMapper.writeValueAsString(event);

            RoutedEventMessage routedMessage =
                    new RoutedEventMessage(eventType, eventPayload);

            String messagePayload =
                    objectMapper.writeValueAsString(routedMessage);

            rabbitTemplate.convertAndSend(
                    "",
                    queue,
                    messagePayload,
                    message -> {
                        message.getMessageProperties()
                                .setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        return message;
                    }
            );

        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Failed to serialise " + eventType
                            + " for queue " + queue,
                    exception
            );
        }
    }
}