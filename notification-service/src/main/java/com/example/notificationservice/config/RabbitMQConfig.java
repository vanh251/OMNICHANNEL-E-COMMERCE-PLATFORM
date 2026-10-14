package com.example.notificationservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "ecommerce.exchange";
    public static final String QUEUE_NAME = "notification.queue";
    public static final String ROUTING_KEY = "order.created";

    @Bean
    public Queue notificationQueue() {
        return new Queue(QUEUE_NAME, true);
    }

    @Bean
    public TopicExchange ecommerceExchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    @Bean
    public Binding binding(Queue notificationQueue, TopicExchange ecommerceExchange) {
        return BindingBuilder.bind(notificationQueue).to(ecommerceExchange).with(ROUTING_KEY);
    }

    @Bean
    public Binding paymentSucceededBinding(Queue notificationQueue, TopicExchange ecommerceExchange) {
        return BindingBuilder.bind(notificationQueue).to(ecommerceExchange).with("payment.succeeded");
    }

    @Bean
    public Binding paymentFailedBinding(Queue notificationQueue, TopicExchange ecommerceExchange) {
        return BindingBuilder.bind(notificationQueue).to(ecommerceExchange).with("payment.failed");
    }

    @Bean
    public Binding orderCancelledBinding(Queue notificationQueue, TopicExchange ecommerceExchange) {
        return BindingBuilder.bind(notificationQueue).to(ecommerceExchange).with("order.cancelled");
    }

    @Bean
    public Binding orderShippedBinding(Queue notificationQueue, TopicExchange ecommerceExchange) {
        return BindingBuilder.bind(notificationQueue).to(ecommerceExchange).with("order.shipped");
    }

    @Bean
    public Binding orderCompletedBinding(Queue notificationQueue, TopicExchange ecommerceExchange) {
        return BindingBuilder.bind(notificationQueue).to(ecommerceExchange).with("order.completed");
    }

    @Bean
    public Binding paymentRefundedBinding(Queue notificationQueue, TopicExchange ecommerceExchange) {
        return BindingBuilder.bind(notificationQueue).to(ecommerceExchange).with("payment.refunded");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
