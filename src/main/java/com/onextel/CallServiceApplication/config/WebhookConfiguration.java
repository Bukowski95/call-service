package com.onextel.CallServiceApplication.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebhookConfiguration {

    private static final int WEBHOOK_CORE_POOL_THREADS = 10;
    private static final int WEBHOOK_MAX_THREADS = 50;
    private static final int KEEP_ALIVE_TIME_SECONDS = 30;
    private static final int WEBHOOK_QUEUE_MAX_EVENTS = 2000;


    //    // For single-instance deployments
//    @Bean
//    @Profile("!cluster")
//    public WebhookEventQueue inMemoryQueue() {
//        return new InMemoryWebhookEventQueue();
//    }
//
// For retries (only if needed)
//    @Bean
//    @ConditionalOnProperty(name = "webhook.retry.enabled", havingValue = "true")
//    public WebhookEventQueue webhookEventQueue(RedisTemplate<String, Object> redisTemplate) {
//        return new RedisWebhookEventQueue(redisTemplate);
//    }

    // For immediate delivery (required)
    @Bean(destroyMethod = "shutdown")
    public ExecutorService webhookExecutor() {
        return new ThreadPoolExecutor(
                WEBHOOK_CORE_POOL_THREADS,                              // Core pool
                WEBHOOK_MAX_THREADS,                                    // Max during peak
                KEEP_ALIVE_TIME_SECONDS, TimeUnit.SECONDS,              // Keep-alive
                new LinkedBlockingQueue<>(WEBHOOK_QUEUE_MAX_EVENTS),    // Backlog
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }


}
