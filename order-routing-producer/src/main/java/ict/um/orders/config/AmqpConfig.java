package ict.um.orders.config;

import ict.um.orders.core_api.config.QueueNames;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AmqpConfig {

    @Bean
    public Queue processingQueue1() {
        return new Queue(QueueNames.QUEUE_1, true);
    }

    @Bean
    public Queue processingQueue2() {
        return new Queue(QueueNames.QUEUE_2, true);
    }

    @Bean
    public Queue processingQueue3() {
        return new Queue(QueueNames.QUEUE_3, true);
    }
}