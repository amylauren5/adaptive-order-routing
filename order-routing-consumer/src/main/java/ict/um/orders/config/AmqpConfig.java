package ict.um.orders.config;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AmqpConfig {

    private static final String HIGH_PRIORITY_QUEUE = "priority.high";
    private static final String MEDIUM_PRIORITY_QUEUE = "priority.medium";
    private static final String LOW_PRIORITY_QUEUE = "priority.low";


    @Bean
    public Queue highPriorityQueue() {
        return new Queue(HIGH_PRIORITY_QUEUE, true);
    }

    @Bean
    public Queue mediumPriorityQueue() {
        return new Queue(MEDIUM_PRIORITY_QUEUE, true);
    }

    @Bean
    public Queue lowPriorityQueue() {
        return new Queue(LOW_PRIORITY_QUEUE, true);
    }

}
