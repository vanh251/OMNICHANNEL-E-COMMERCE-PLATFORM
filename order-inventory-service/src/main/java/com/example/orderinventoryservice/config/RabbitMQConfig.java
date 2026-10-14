package com.example.orderinventoryservice.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class RabbitMQConfig {
    @Bean
    public ObjectMapper eventObjectMapper() { return new ObjectMapper(); }

    public static final String EXCHANGE_NAME = "ecommerce.exchange";
    public static final String INVENTORY_SYNC_KEY = "catalog.inventory.sync";
    public static final String INVENTORY_SYNC_QUEUE = "catalog.inventory.sync.queue";
    public static final String INVENTORY_ACK_KEY = "catalog.inventory.synced";
    public static final String INVENTORY_ACK_QUEUE = "catalog.inventory.synced.queue";
    public static final String INVENTORY_SYNC_FAILED_KEY = "catalog.inventory.sync.failed";
    public static final String INVENTORY_ACK_FAILED_KEY = "catalog.inventory.synced.failed";

    @Bean
    public Queue orderQueue() { return new Queue("order.queue", true); }

    @Bean
    public Queue paymentQueue() { return new Queue("payment.queue", true); }

    @Bean
    public Queue notificationQueue() { return new Queue("notification.queue", true); }

    @Bean
    public Queue inventorySyncQueue() {
        return QueueBuilder.durable(INVENTORY_SYNC_QUEUE).deadLetterExchange(EXCHANGE_NAME)
                .deadLetterRoutingKey(INVENTORY_SYNC_FAILED_KEY).build();
    }

    @Bean
    public Queue inventoryAckQueue() {
        return QueueBuilder.durable(INVENTORY_ACK_QUEUE).deadLetterExchange(EXCHANGE_NAME)
                .deadLetterRoutingKey(INVENTORY_ACK_FAILED_KEY).build();
    }

    @Bean
    public Queue inventorySyncDeadLetterQueue() { return new Queue("catalog.inventory.sync.dlq", true); }

    @Bean
    public Queue inventoryAckDeadLetterQueue() { return new Queue("catalog.inventory.synced.dlq", true); }

    @Bean
    public Binding inventorySyncBinding(@Qualifier("inventorySyncQueue") Queue queue, TopicExchange ecommerceExchange) {
        return BindingBuilder.bind(queue).to(ecommerceExchange).with(INVENTORY_SYNC_KEY);
    }

    @Bean
    public Binding inventoryAckBinding(@Qualifier("inventoryAckQueue") Queue queue, TopicExchange ecommerceExchange) {
        return BindingBuilder.bind(queue).to(ecommerceExchange).with(INVENTORY_ACK_KEY);
    }

    @Bean
    public Binding inventorySyncDeadLetterBinding(@Qualifier("inventorySyncDeadLetterQueue") Queue queue, TopicExchange ecommerceExchange) {
        return BindingBuilder.bind(queue).to(ecommerceExchange).with(INVENTORY_SYNC_FAILED_KEY);
    }

    @Bean
    public Binding inventoryAckDeadLetterBinding(@Qualifier("inventoryAckDeadLetterQueue") Queue queue, TopicExchange ecommerceExchange) {
        return BindingBuilder.bind(queue).to(ecommerceExchange).with(INVENTORY_ACK_FAILED_KEY);
    }

    @Bean
    public Binding paymentSucceededBinding(@Qualifier("orderQueue") Queue orderQueue, TopicExchange ecommerceExchange) {
        return BindingBuilder.bind(orderQueue).to(ecommerceExchange).with("payment.succeeded");
    }

    @Bean
    public Binding paymentFailedBinding(@Qualifier("orderQueue") Queue orderQueue, TopicExchange ecommerceExchange) {
        return BindingBuilder.bind(orderQueue).to(ecommerceExchange).with("payment.failed");
    }

    @Bean
    public Binding orderCreatedPaymentBinding(@Qualifier("paymentQueue") Queue paymentQueue, TopicExchange ecommerceExchange) {
        return BindingBuilder.bind(paymentQueue).to(ecommerceExchange).with("order.created");
    }

    @Bean
    public Binding orderCreatedNotificationBinding(@Qualifier("notificationQueue") Queue notificationQueue, TopicExchange ecommerceExchange) {
        return BindingBuilder.bind(notificationQueue).to(ecommerceExchange).with("order.created");
    }

    @Bean
    public Binding orderCancelledNotificationBinding(@Qualifier("notificationQueue") Queue notificationQueue, TopicExchange ecommerceExchange) {
        return BindingBuilder.bind(notificationQueue).to(ecommerceExchange).with("order.cancelled");
    }

    @Bean
    public Binding orderShippedNotificationBinding(@Qualifier("notificationQueue") Queue notificationQueue, TopicExchange ecommerceExchange) {
        return BindingBuilder.bind(notificationQueue).to(ecommerceExchange).with("order.shipped");
    }

    @Bean
    public Binding orderCompletedNotificationBinding(@Qualifier("notificationQueue") Queue notificationQueue, TopicExchange ecommerceExchange) {
        return BindingBuilder.bind(notificationQueue).to(ecommerceExchange).with("order.completed");
    }

    @Bean
    public TopicExchange ecommerceExchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
