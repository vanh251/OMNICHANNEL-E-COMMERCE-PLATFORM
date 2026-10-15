package com.example.identityservice.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IdentityRabbitConfig {
    public static final String EXCHANGE = "ecommerce.exchange";
    @Bean
    public TopicExchange identityExchange() { return new TopicExchange(EXCHANGE); }
}
