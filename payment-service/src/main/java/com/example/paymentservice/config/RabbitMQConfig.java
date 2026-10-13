package com.example.paymentservice.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class RabbitMQConfig {
    @Bean
    public ObjectMapper eventObjectMapper() { return new ObjectMapper(); }
    @Bean
    public TopicExchange ecommerceExchange() { return new TopicExchange("ecommerce.exchange"); }

    @Bean
    public Queue paymentQueue() { return new Queue("payment.queue", true); }

    @Bean
    public Queue orderQueue() { return new Queue("order.queue", true); }

    @Bean
    public Queue notificationQueue() { return new Queue("notification.queue", true); }

    @Bean
    public Binding orderCreatedBinding(@Qualifier("paymentQueue") Queue paymentQueue, TopicExchange ecommerceExchange) {
        return BindingBuilder.bind(paymentQueue).to(ecommerceExchange).with("order.created");
    }

    @Bean
    public Binding orderCancelledPaymentBinding(@Qualifier("paymentQueue") Queue queue, TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with("order.cancelled");
    }

    @Bean
    public Binding paymentRefundedNotificationBinding(@Qualifier("notificationQueue") Queue queue, TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with("payment.refunded");
    }

    @Bean
    public Binding paymentSucceededOrderBinding(@Qualifier("orderQueue") Queue orderQueue, TopicExchange ecommerceExchange) {
        return BindingBuilder.bind(orderQueue).to(ecommerceExchange).with("payment.succeeded");
    }

    @Bean
    public Binding paymentFailedOrderBinding(@Qualifier("orderQueue") Queue orderQueue, TopicExchange ecommerceExchange) {
        return BindingBuilder.bind(orderQueue).to(ecommerceExchange).with("payment.failed");
    }

    @Bean
    public Binding paymentSucceededNotificationBinding(@Qualifier("notificationQueue") Queue notificationQueue, TopicExchange ecommerceExchange) {
        return BindingBuilder.bind(notificationQueue).to(ecommerceExchange).with("payment.succeeded");
    }

    @Bean
    public Binding paymentFailedNotificationBinding(@Qualifier("notificationQueue") Queue notificationQueue, TopicExchange ecommerceExchange) {
        return BindingBuilder.bind(notificationQueue).to(ecommerceExchange).with("payment.failed");
    }
}
