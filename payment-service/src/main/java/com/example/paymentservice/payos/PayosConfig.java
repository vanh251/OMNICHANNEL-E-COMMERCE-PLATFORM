package com.example.paymentservice.payos;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;
import vn.payos.PayOS;
import vn.payos.core.ClientOptions;

@Configuration
@ConditionalOnProperty(name = "payment.provider", havingValue = "payos")
public class PayosConfig {
    @Bean(destroyMethod = "close")
    public PayOS payos(@Value("${payos.client-id}") String clientId,
                       @Value("${payos.api-key}") String apiKey,
                       @Value("${payos.checksum-key}") String checksumKey,
                       @Value("${payos.timeout-ms:10000}") int timeout) {
        Assert.hasText(clientId, "PAYOS_CLIENT_ID is required");
        Assert.hasText(apiKey, "PAYOS_API_KEY is required");
        Assert.hasText(checksumKey, "PAYOS_CHECKSUM_KEY is required");
        Assert.isTrue(timeout > 0, "payOS timeout must be positive");
        return new PayOS(ClientOptions.builder().clientId(clientId).apiKey(apiKey)
                .checksumKey(checksumKey).timeoutMs(timeout).maxRetries(0)
                .logLevel(ClientOptions.LogLevel.NONE).build());
    }
}
