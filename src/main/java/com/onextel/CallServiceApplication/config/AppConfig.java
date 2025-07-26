package com.onextel.CallServiceApplication.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.onextel.CallServiceApplication.freeswitch.loadbalancing.LoadBalancer;
import com.onextel.CallServiceApplication.service.redis.CallStateBatchUpdater;
import com.onextel.CallServiceApplication.service.redis.RedisCallMetricsService;
import com.onextel.CallServiceApplication.service.redis.RedisConnectionPool;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestTemplate;

import java.time.Clock;

@Configuration
public class AppConfig {

    @Value("${app.freeswitch.loadbalancer.strategy:LEAST_SESSIONS}")
    private String strategy;

    @Bean
    public LoadBalancer loadBalancer() {    // for CallService
        // Parse the strategy and initialize the LoadBalancer
        LoadBalancer.Strategy selectedStrategy = LoadBalancer.Strategy.valueOf(strategy);
        return new LoadBalancer(selectedStrategy);
    }

    @Bean
    public RetryTemplate retryTemplate() {  // for WebhookRetryEngine
        return new RetryTemplate();
    }

    @Bean
    public RestTemplate restTemplate() {  // for WebhookDeliveryService
        return new RestTemplate();
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        // Register the JavaTimeModule to handle Java 8 DateTime API
        // types like Instant, LocalDateTime, etc.
        objectMapper.registerModule(new JavaTimeModule());
        // Disable serialization of dates as timestamps (which are long values)
        // to ensure ISO-8601 format
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return objectMapper;
    }

    @Bean
    public Clock systemClock() {          // for RedisWebhookRegistry
        return Clock.systemDefaultZone();
    }

    @Bean
    public CallStateBatchUpdater callStateBatchUpdater(RedisConnectionPool connectionPool) {
        return new CallStateBatchUpdater(connectionPool);
    }

    @Bean
    public RedisCallMetricsService redisCallMetricsService(
            RedisConnectionPool connectionPool,
            CallStateBatchUpdater batchUpdater,
            MeterRegistry meterRegistry) {
        return new RedisCallMetricsService(connectionPool, batchUpdater, meterRegistry);
    }
}
