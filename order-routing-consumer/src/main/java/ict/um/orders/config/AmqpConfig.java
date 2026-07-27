package ict.um.orders.config;

import ict.um.orders.core_api.config.QueueNames;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AmqpConfig {

    @Bean
    public Queue highPriorityQueue() {
        return new Queue(QueueNames.HIGH, true);
    }

    @Bean
    public Queue mediumPriorityQueue() {
        return new Queue(QueueNames.MEDIUM, true);
    }

    @Bean
    public Queue lowPriorityQueue() {
        return new Queue(QueueNames.LOW, true);
    }
}