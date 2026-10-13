package com.example.productservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;

@Configuration
public class CatalogRabbitConfig {
    public static final String EXCHANGE = "ecommerce.exchange";
    public static final String SYNC_KEY = "catalog.inventory.sync";
    public static final String SYNC_QUEUE = "catalog.inventory.sync.queue";
    public static final String ACK_KEY = "catalog.inventory.synced";
    public static final String ACK_QUEUE = "catalog.inventory.synced.queue";
    public static final String SYNC_FAILED_KEY = "catalog.inventory.sync.failed";
    public static final String ACK_FAILED_KEY = "catalog.inventory.synced.failed";

    @Bean
    public ObjectMapper catalogEventObjectMapper() { return new ObjectMapper(); }

    @Bean
    public TopicExchange catalogExchange() { return new TopicExchange(EXCHANGE); }

    @Bean
    public Queue catalogInventorySyncQueue() {
        return QueueBuilder.durable(SYNC_QUEUE).deadLetterExchange(EXCHANGE)
                .deadLetterRoutingKey(SYNC_FAILED_KEY).build();
    }

    @Bean
    public Queue catalogInventoryAckQueue() {
        return QueueBuilder.durable(ACK_QUEUE).deadLetterExchange(EXCHANGE)
                .deadLetterRoutingKey(ACK_FAILED_KEY).build();
    }

    @Bean
    public Queue catalogInventorySyncDeadLetterQueue() { return new Queue("catalog.inventory.sync.dlq", true); }

    @Bean
    public Queue catalogInventoryAckDeadLetterQueue() { return new Queue("catalog.inventory.synced.dlq", true); }

    @Bean
    public Binding catalogInventorySyncBinding(@Qualifier("catalogInventorySyncQueue") Queue catalogInventorySyncQueue, TopicExchange catalogExchange) {
        return BindingBuilder.bind(catalogInventorySyncQueue).to(catalogExchange).with(SYNC_KEY);
    }

    @Bean
    public Binding catalogInventoryAckBinding(@Qualifier("catalogInventoryAckQueue") Queue catalogInventoryAckQueue, TopicExchange catalogExchange) {
        return BindingBuilder.bind(catalogInventoryAckQueue).to(catalogExchange).with(ACK_KEY);
    }

    @Bean
    public Binding catalogInventorySyncDeadLetterBinding(@Qualifier("catalogInventorySyncDeadLetterQueue") Queue queue, TopicExchange catalogExchange) {
        return BindingBuilder.bind(queue).to(catalogExchange).with(SYNC_FAILED_KEY);
    }

    @Bean
    public Binding catalogInventoryAckDeadLetterBinding(@Qualifier("catalogInventoryAckDeadLetterQueue") Queue queue, TopicExchange catalogExchange) {
        return BindingBuilder.bind(queue).to(catalogExchange).with(ACK_FAILED_KEY);
    }
}
