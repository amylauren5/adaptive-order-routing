package ict.um.pharmaceutical.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AmqpConfig {

    private static final String BLOCKCHAIN_UPLOAD_QUEUE = "blockchain.upload.queue";
    private static final String BLOCKCHAIN_SELL_QUEUE = "blockchain.sell.queue";
    private static final String BLOCKCHAIN_SHIPMENT_QUEUE = "blockchain.shipment.queue";
    private static final String BLOCKCHAIN_DELIVERY_QUEUE = "blockchain.delivery.queue";
    private static final String BLOCKCHAIN_CANCEL_QUEUE = "blockchain.cancel.queue";

    @Bean
    public Queue uploadQueue() {
        return new Queue(BLOCKCHAIN_UPLOAD_QUEUE, true);
    }

    @Bean
    public Queue sellQueue() {
        return new Queue(BLOCKCHAIN_SELL_QUEUE, true);
    }

    @Bean
    public Queue shipmentQueue() {
        return new Queue(BLOCKCHAIN_SHIPMENT_QUEUE, true);
    }

    @Bean
    public Queue deliveryQueue() {
        return new Queue(BLOCKCHAIN_DELIVERY_QUEUE, true);
    }

    @Bean
    public Queue cancelQueue() {
        return new Queue(BLOCKCHAIN_CANCEL_QUEUE, true);
    }
}
