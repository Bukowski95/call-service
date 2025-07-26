package com.onextel.CallServiceApplication.service.webhook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.onextel.CallServiceApplication.common.JsonUtil;
import com.onextel.CallServiceApplication.model.webhook.WebhookConfig;
import com.onextel.CallServiceApplication.model.webhook.WebhookEvent;
import com.onextel.CallServiceApplication.model.webhook.WebhookEventType;
import io.lettuce.core.api.StatefulRedisConnection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisWebhookDeliveryLog {
    // TODO: create a log file for delivered events as well

    private static final String FAILURE_LOG = "webhook:failures";
    private static final String SUCCESS_LOG = "webhook:successes";
    private static final int MAX_LOG_ENTRIES = 1000;

    private final StatefulRedisConnection<String, String> redisConnection;

    public void logFailure(WebhookEvent event, WebhookConfig config,
                           int attemptCount, String reason) {
        try {
            String logEntry = JsonUtil.serialize(
                    new DeliveryLogEntry(
                            Instant.now(),
                            config.getUrl(),
                            config.getAccountId(),
                            event.getType(),
                            attemptCount,
                            reason,
                            false
                    )
            );
            logFailedDelivery(logEntry);
            log.error("webhook event delivery failed {}", logEntry);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize failure log entry", e);
        }
    }

    public void logSuccess(WebhookEvent event, WebhookConfig config,
                           int attemptCount, Duration duration) {
        try {

            String logEntry = JsonUtil.serialize(
                    new DeliveryLogEntry(
                            Instant.now(),
                            config.getUrl(),
                            config.getAccountId(),
                            event.getType(),
                            attemptCount,
                            duration.toMillis() + "ms",
                            true
                    )
            );
            logSuccessfulDelivery(logEntry);
            log.info("webhook event delivered {}", logEntry);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize success log entry", e);
        }
    }

    private void logFailedDelivery(String logEntry) {
        redisConnection.sync().multi();
        redisConnection.sync().rpush(FAILURE_LOG, logEntry);
        redisConnection.sync().ltrim(FAILURE_LOG, 0, MAX_LOG_ENTRIES - 1);
        redisConnection.sync().exec();
    }

    private void logSuccessfulDelivery(String logEntry) {
        redisConnection.sync().multi();
        redisConnection.sync().rpush(SUCCESS_LOG, logEntry);
        redisConnection.sync().ltrim(SUCCESS_LOG, 0, MAX_LOG_ENTRIES - 1);
        redisConnection.sync().exec();
    }

    private record DeliveryLogEntry(Instant timestamp, String url, String accountId, WebhookEventType eventType,
                                        int attemptCount, String details, boolean success) {
    }
}
